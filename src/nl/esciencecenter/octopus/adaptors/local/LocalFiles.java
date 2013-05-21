/*
 * Copyright 2013 Netherlands eScience Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.esciencecenter.octopus.adaptors.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import nl.esciencecenter.octopus.credentials.Credential;
import nl.esciencecenter.octopus.engine.OctopusProperties;
import nl.esciencecenter.octopus.engine.files.FileSystemImplementation;
import nl.esciencecenter.octopus.engine.files.FilesEngine;
import nl.esciencecenter.octopus.engine.files.AbsolutePathImplementation;
import nl.esciencecenter.octopus.exceptions.DirectoryNotEmptyException;
import nl.esciencecenter.octopus.exceptions.FileAlreadyExistsException;
import nl.esciencecenter.octopus.exceptions.IllegalSourcePathException;
import nl.esciencecenter.octopus.exceptions.NoSuchFileException;
import nl.esciencecenter.octopus.exceptions.OctopusException;
import nl.esciencecenter.octopus.exceptions.OctopusIOException;
import nl.esciencecenter.octopus.exceptions.OctopusRuntimeException;
import nl.esciencecenter.octopus.exceptions.UnsupportedOperationException;
import nl.esciencecenter.octopus.exceptions.IllegalTargetPathException;
import nl.esciencecenter.octopus.exceptions.InvalidDataException;
import nl.esciencecenter.octopus.files.DirectoryStream;
import nl.esciencecenter.octopus.files.FileAttributes;
import nl.esciencecenter.octopus.files.FileSystem;
import nl.esciencecenter.octopus.files.OpenOption;
import nl.esciencecenter.octopus.files.AbsolutePath;
import nl.esciencecenter.octopus.files.PathAttributesPair;
import nl.esciencecenter.octopus.files.PosixFilePermission;
import nl.esciencecenter.octopus.files.RelativePath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalFiles implements nl.esciencecenter.octopus.files.Files {

    /** A logger for this class */
    private static final Logger logger = LoggerFactory.getLogger(LocalFiles.class);

    /** The parent adaptor */
    private final LocalAdaptor localAdaptor;

    /** The next ID for a FileSystem */
    private static int fsID = 0;

    private static synchronized int getNextFsID() {
        return fsID++;
    }

    public LocalFiles(OctopusProperties properties, LocalAdaptor localAdaptor) {
        this.localAdaptor = localAdaptor;

        if (logger.isDebugEnabled()) {
            Set<String> attributeViews = FileSystems.getDefault().supportedFileAttributeViews();

            logger.debug(Arrays.toString(attributeViews.toArray()));
        }
    }

    /**
     * Copy an existing source file or link to a non-existing target path.
     * 
     * The source must NOT be a directory.
     * 
     * The parent of the target path (e.g. <code>target.getParent</code>) must exist.
     * 
     * If the source is a link, the link itself will be copied, not the path to which it refers.
     * 
     * @param source
     *            the existing source file or link.
     * @param target
     *            the non existing target path.
     * @return the target path.
     * 
     * @throws NoSuchFileException
     *             If the source file does not exist or the target parent directory does not exist.
     * @throws FileAlreadyExistsException
     *             If the target file already exists.
     * @throws IllegalSourcePathException
     *             If the source is a directory.
     * @throws OctopusIOException
     *             If the move failed.
     */
    @Override
    public AbsolutePath copy(AbsolutePath source, AbsolutePath target) throws OctopusIOException {

        if (!exists(source)) {
            throw new NoSuchFileException(LocalAdaptor.ADAPTOR_NAME, "Source " + source.getPath() + " does not exist!");
        }

        if (isDirectory(source)) {
            throw new IllegalSourcePathException(LocalAdaptor.ADAPTOR_NAME, "Source " + source.getPath() + " is a directory");
        }

        if (exists(target)) {
            throw new FileAlreadyExistsException(LocalAdaptor.ADAPTOR_NAME, "Target " + target.getPath() + " already exists!");
        }

        if (!exists(target.getParent())) {
            throw new NoSuchFileException(LocalAdaptor.ADAPTOR_NAME, "Target directory " + target.getParent().getPath()
                    + " does not exist!");
        }

        if (source.normalize().equals(target.normalize())) {
            return target;
        }

        try {
            Files.copy(LocalUtils.javaPath(source), LocalUtils.javaPath(target));
        } catch (IOException e) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Failed to copy " + source.getPath() + " to "
                    + target.getPath(), e);
        }

        return target;
    }

    private void append(AbsolutePath source, long fromOffset, AbsolutePath target) throws OctopusIOException { 

        // We need to append some bytes from source to target. 
        try (InputStream in = Files.newInputStream(LocalUtils.javaPath(source), StandardOpenOption.READ);
             OutputStream out = Files.newOutputStream(LocalUtils.javaPath(target), StandardOpenOption.APPEND)) {
            
            in.skip(fromOffset);
            
            byte [] buffer = new byte[4*1024];
            
            int size = in.read(buffer);
            
            while (size > 0) { 
                out.write(buffer, 0, size);
                size = in.read(buffer);                
            }
            
        } catch (IOException e) { 
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Failed to copy " + source.getPath() + ":" + fromOffset + 
                    " to target " + target.getPath(), e);
        }          
    }
    
    @Override
    public AbsolutePath resumeCopy(AbsolutePath source, AbsolutePath target, boolean check) throws OctopusIOException {
        
        if (!exists(source)) {
            throw new NoSuchFileException(LocalAdaptor.ADAPTOR_NAME, "Source " + source.getPath() + " does not exist!");
        }
        
        if (isDirectory(source)) {
            throw new IllegalSourcePathException(LocalAdaptor.ADAPTOR_NAME, "Source " + source.getPath() + " is a directory");
        }

        if (isSymbolicLink(source)) {
            throw new IllegalSourcePathException(LocalAdaptor.ADAPTOR_NAME, "Source " + source.getPath() + " is a link");
        }
        
        if (!exists(target)) {
            throw new NoSuchFileException(LocalAdaptor.ADAPTOR_NAME, "Target " + target.getPath() + " does not exist!");
        }

        if (isDirectory(target)) {
            throw new IllegalSourcePathException(LocalAdaptor.ADAPTOR_NAME, "Target " + target.getPath() + " is a directory");
        }

        if (isSymbolicLink(source)) {
            throw new IllegalSourcePathException(LocalAdaptor.ADAPTOR_NAME, "Target " + target.getPath() + " is a link");
        }

        if (source.normalize().equals(target.normalize())) {
            return target;
        }
        
        long targetSize = size(target); 
        long sourceSize = size(source);

        // If target is larger than source, they cannot be the same file.
        if (targetSize > sourceSize) {
            throw new InvalidDataException(LocalAdaptor.ADAPTOR_NAME, "Data in target " + target.getPath() + 
                    " does not match " + source + " " + source.getPath());
        }
            
        if (check) {
            // check if the data in target corresponds to the head of source.
            try { 
                if (!LocalUtils.isHead(LocalUtils.javaPath(target), LocalUtils.javaPath(source))) { 
                    throw new InvalidDataException(LocalAdaptor.ADAPTOR_NAME, "Data in target " + target.getPath() + 
                            " does not match source " + source.getPath());
                }
            } catch (IOException e) {
                throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Failed to compare " + source.getPath() + " to "
                        + target.getPath(), e);
            }
        }

        // If target is the same size as source we are done.
        if (targetSize == sourceSize) { 
            return target;
        }
        
        // Now append source (from index targetSize) to target.
        append(source, targetSize, target);
        
        return target;
    }
    
    /* (non-Javadoc)
     * @see nl.esciencecenter.octopus.files.Files#append(nl.esciencecenter.octopus.files.AbsolutePath, nl.esciencecenter.octopus.files.AbsolutePath)
     */
    @Override
    public AbsolutePath append(AbsolutePath source, AbsolutePath target) throws OctopusIOException {
        
        if (!exists(source)) {
            throw new NoSuchFileException(LocalAdaptor.ADAPTOR_NAME, "Source " + source.getPath() + " does not exist!");
        }
        
        if (isDirectory(source)) {
            throw new IllegalSourcePathException(LocalAdaptor.ADAPTOR_NAME, "Source " + source.getPath() + " is a directory");
        }

        if (!exists(target)) {
            throw new NoSuchFileException(LocalAdaptor.ADAPTOR_NAME, "Target " + target.getPath() + " does not exist!");
        }

        if (isDirectory(target)) {
            throw new IllegalSourcePathException(LocalAdaptor.ADAPTOR_NAME, "Target " + target.getPath() + " is a directory");
        }

        if (source.normalize().equals(target.normalize())) {
            throw new IllegalTargetPathException(LocalAdaptor.ADAPTOR_NAME, "Can not append a file to itself (source " + 
                    source.getPath() + " equals target " + target.getPath() + ")");
        }

        append(source, 0, target);
        
        return target;
    }


    /**
     * Move or rename an existing source path to a non-existing target path.
     * 
     * The parent of the target path (e.g. <code>target.getParent</code>) must exist.
     * 
     * If the source is a link, the link itself will be moved, not the path to which it refers. If the source is a directory, it
     * will be renamed to the target. This implies that a moving a directory between physical locations may fail.
     * 
     * @param source
     *            the existing source path.
     * @param target
     *            the non existing target path.
     * @return the target path.
     * 
     * @throws NoSuchFileException
     *             If the source file does not exist or the target parent directory does not exist.
     * @throws FileAlreadyExistsException
     *             If the target file already exists.
     * @throws OctopusIOException
     *             If the move failed.
     */
    @Override
    public AbsolutePath move(AbsolutePath source, AbsolutePath target) throws OctopusIOException {

        if (!exists(source)) {
            throw new NoSuchFileException(LocalAdaptor.ADAPTOR_NAME, "Source " + source.getPath() + " does not exist!");
        }

        if (exists(target)) {
            throw new FileAlreadyExistsException(LocalAdaptor.ADAPTOR_NAME, "Target " + target.getPath() + " already exists!");
        }

        if (!exists(target.getParent())) {
            throw new NoSuchFileException(LocalAdaptor.ADAPTOR_NAME, "Target directory " + target.getParent().getPath()
                    + " does not exist!");
        }

        if (source.normalize().equals(target.normalize())) {
            return target;
        }

        try {
            Files.move(LocalUtils.javaPath(source), LocalUtils.javaPath(target));
        } catch (IOException e) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Failed to move " + source.getPath() + " to "
                    + target.getPath(), e);
        }

        return target;
    }

    @Override
    public AbsolutePath createSymbolicLink(AbsolutePath link, AbsolutePath target) throws OctopusIOException {

        if (exists(link)) {
            throw new FileAlreadyExistsException(LocalAdaptor.ADAPTOR_NAME, "Target already exists.");
        }

        try {
            Files.createSymbolicLink(LocalUtils.javaPath(link), LocalUtils.javaPath(target));
        } catch (IOException e) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Failed to create symbolic link.", e);
        }

        return link;
    }

    @Override
    public AbsolutePath readSymbolicLink(AbsolutePath link) throws OctopusIOException {

        try {
            java.nio.file.Path target = Files.readSymbolicLink(LocalUtils.javaPath(link));

            // FIXME: No clue if this is correct!!
            return new AbsolutePathImplementation(link.getFileSystem(), new RelativePath(target.toString()));
        } catch (IOException e) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Failed to read symbolic link.", e);
        }
    }

    @Override
    public DirectoryStream<AbsolutePath> newDirectoryStream(AbsolutePath dir, DirectoryStream.Filter filter)
            throws OctopusIOException {

        if (!isDirectory(dir)) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "File is not a directory.");
        }

        return new LocalDirectoryStream(dir, filter);
    }

    @Override
    public DirectoryStream<PathAttributesPair> newAttributesDirectoryStream(AbsolutePath dir, DirectoryStream.Filter filter)
            throws OctopusIOException {

        if (!isDirectory(dir)) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "File is not a directory.");
        }

        return new LocalDirectoryAttributeStream(this, dir, filter);
    }

    @Override
    public InputStream newInputStream(AbsolutePath path) throws OctopusIOException {

        try {
            return Files.newInputStream(LocalUtils.javaPath(path));
        } catch (IOException e) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Failed to create InputStream.", e);
        }
    }

    @Override
    public OutputStream newOutputStream(AbsolutePath path, OpenOption... options) throws OctopusIOException {

        try {
            return Files.newOutputStream(LocalUtils.javaPath(path), LocalUtils.javaOpenOptions(options));
        } catch (IOException e) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Failed to create OutputStream.", e);
        }
    }

    @Override
    public FileAttributes getAttributes(AbsolutePath path) throws OctopusIOException {
        return new LocalFileAttributes(path);
    }

    @Override
    public boolean exists(AbsolutePath path) throws OctopusIOException {
        return Files.exists(LocalUtils.javaPath(path));
    }

    @Override
    public boolean isDirectory(AbsolutePath path) throws OctopusIOException {
        return Files.isDirectory(LocalUtils.javaPath(path));
    }

    @Override
    public void setOwner(AbsolutePath path, String user, String group) throws OctopusIOException {

        try {
            PosixFileAttributeView view = Files.getFileAttributeView(LocalUtils.javaPath(path), PosixFileAttributeView.class);

            if (user != null) {
                UserPrincipal userPrincipal =
                        FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName(user);

                view.setOwner(userPrincipal);
            }

            if (group != null) {
                GroupPrincipal groupPrincipal =
                        FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByGroupName(group);

                view.setGroup(groupPrincipal);
            }
        } catch (IOException e) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Failed to set user and group.", e);
        }

    }

    @Override
    public void setPosixFilePermissions(AbsolutePath path, Set<PosixFilePermission> permissions) throws OctopusIOException {

        try {
            PosixFileAttributeView view = Files.getFileAttributeView(LocalUtils.javaPath(path), PosixFileAttributeView.class);
            view.setPermissions(LocalUtils.javaPermissions(permissions));
        } catch (IOException e) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Failed to set permissions", e);
        }
    }

    @Override
    public void setFileTimes(AbsolutePath path, long lastModifiedTime, long lastAccessTime, long createTime)
            throws OctopusIOException {

        try {
            PosixFileAttributeView view = Files.getFileAttributeView(LocalUtils.javaPath(path), PosixFileAttributeView.class);

            FileTime lastModifiedFileTime = null;
            FileTime lastAccessFileTime = null;
            FileTime createFileTime = null;

            if (lastModifiedTime != -1) {
                lastModifiedFileTime = FileTime.fromMillis(lastModifiedTime);
            }

            if (lastAccessTime != -1) {
                lastAccessFileTime = FileTime.fromMillis(lastAccessTime);
            }

            if (createTime != -1) {
                createFileTime = FileTime.fromMillis(createTime);
            }

            view.setTimes(lastModifiedFileTime, lastAccessFileTime, createFileTime);

        } catch (IOException e) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Failed to set file times.", e);
        }
    }

    @Override
    public FileSystem newFileSystem(URI location, Credential credential, Properties properties) throws OctopusException,
            OctopusIOException {

        localAdaptor.checkURI(location);

        String path = location.getPath();

        if (path != null && !path.equals("/")) {
            throw new OctopusException(LocalAdaptor.ADAPTOR_NAME, "Cannot create local file system with path!");
        }

        path = System.getProperty("user.home");

        if (!LocalUtils.exists(path)) {
            throw new OctopusException(LocalAdaptor.ADAPTOR_NAME, "Cannot create FileSystem with non-existing entry path ("
                    + path + ")");
        }

        return new FileSystemImplementation(LocalAdaptor.ADAPTOR_NAME, "localfs-" + getNextFsID(), location, 
                new RelativePath(path), credential, new OctopusProperties(properties));
    }

    @Override
    public AbsolutePath newPath(FileSystem filesystem, RelativePath location) throws OctopusException, OctopusIOException {
        return new AbsolutePathImplementation(filesystem, location);
    }

    @Override
    public void close(FileSystem filesystem) throws OctopusException, OctopusIOException {
        // ignored!
    }

    @Override
    public boolean isOpen(FileSystem filesystem) throws OctopusException, OctopusIOException {
        return true;
    }

    @Override
    public AbsolutePath createDirectories(AbsolutePath dir) throws OctopusIOException {

        Iterator<AbsolutePath> itt = dir.iterator();

        while (itt.hasNext()) {
            AbsolutePath path = itt.next();

            if (!exists(path)) {
                createDirectory(path);
            }
        }

        return dir;
    }

    @Override
    public AbsolutePath createDirectory(AbsolutePath dir) throws OctopusIOException {

        if (exists(dir)) {
            throw new FileAlreadyExistsException(LocalAdaptor.ADAPTOR_NAME, "Directory " + dir.getPath() + " already exists!");
        }

        if (!exists(dir.getParent())) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Parent directory " + dir.getParent() + " does not exist!");
        }

        try {
            java.nio.file.Files.createDirectory(LocalUtils.javaPath(dir));
        } catch (IOException e) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Failed to create directory " + dir.getPath(), e);
        }

        return dir;
    }

    @Override
    public AbsolutePath createFile(AbsolutePath path) throws OctopusIOException {

        if (exists(path)) {
            throw new FileAlreadyExistsException(LocalAdaptor.ADAPTOR_NAME, "File " + path.getPath() + " already exists!");
        }

        if (!exists(path.getParent())) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Parent directory " + path.getParent() + " does not exist!");
        }

        try {
            java.nio.file.Files.createFile(LocalUtils.javaPath(path));
        } catch (IOException e) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Failed to create file " + path.getPath(), e);
        }

        return path;
    }

    @Override
    public void delete(AbsolutePath path) throws OctopusIOException {

        try {
            java.nio.file.Files.delete(LocalUtils.javaPath(path));
        } catch (java.nio.file.NoSuchFileException e1) {
            throw new NoSuchFileException(LocalAdaptor.ADAPTOR_NAME, "File " + path.getPath() + " does not exist!");

        } catch (java.nio.file.DirectoryNotEmptyException e2) {
            throw new DirectoryNotEmptyException(LocalAdaptor.ADAPTOR_NAME, "Directory " + path.getPath() + " not empty!");

        } catch (Exception e) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Failed to delete file " + path.getPath(), e);
        }
    }

    @Override
    public DirectoryStream<AbsolutePath> newDirectoryStream(AbsolutePath dir) throws OctopusIOException {
        return newDirectoryStream(dir, FilesEngine.ACCEPT_ALL_FILTER);
    }

    @Override
    public DirectoryStream<PathAttributesPair> newAttributesDirectoryStream(AbsolutePath dir) throws OctopusIOException {
        return newAttributesDirectoryStream(dir, FilesEngine.ACCEPT_ALL_FILTER);
    }

    @Override
    public SeekableByteChannel newByteChannel(AbsolutePath path, OpenOption... options) throws OctopusIOException {

        try {
            return java.nio.file.Files.newByteChannel(LocalUtils.javaPath(path), LocalUtils.javaOpenOptions(options));
        } catch (Exception e) {
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Failed to create byte channel " + path.getPath(), e);
        }
    }

    @Override
    public FileSystem getLocalCWDFileSystem() throws OctopusException {

        String path = System.getProperty("user.dir");

        if (!LocalUtils.exists(path)) {
            throw new OctopusException(LocalAdaptor.ADAPTOR_NAME, 
                    "Cannot create FileSystem with non-existing CWD (" + path + ")");
        }

        URI uri = null;

        try {
            uri = new URI("file:///");
        } catch (URISyntaxException e) {
            throw new OctopusRuntimeException(LocalAdaptor.ADAPTOR_NAME, "Failed to create URI", e);
        }

        return new FileSystemImplementation(LocalAdaptor.ADAPTOR_NAME, "localfs-" + getNextFsID(), uri, 
                new RelativePath(path), null, null);
    }

    @Override
    public FileSystem getLocalHomeFileSystem() throws OctopusException {

        String path = System.getProperty("user.home");

        if (!LocalUtils.exists(path)) {
            throw new OctopusException(LocalAdaptor.ADAPTOR_NAME, "Cannot create FileSystem with non-existing home directory ("
                    + path + ")");
        }

        URI uri = null;

        try {
            uri = new URI("file:///");
        } catch (URISyntaxException e) {
            throw new OctopusRuntimeException(LocalAdaptor.ADAPTOR_NAME, "Failed to create URI", e);
        }

        return new FileSystemImplementation(LocalAdaptor.ADAPTOR_NAME, "localfs-" + getNextFsID(), uri, new RelativePath(path),
                null, null);
    }


    /* (non-Javadoc)
     * @see nl.esciencecenter.octopus.files.Files#isSymbolicLink(nl.esciencecenter.octopus.files.AbsolutePath)
     */
    @Override
    public boolean isSymbolicLink(AbsolutePath path) throws OctopusIOException {
        return Files.isSymbolicLink(LocalUtils.javaPath(path));
    }

    /* (non-Javadoc)
     * @see nl.esciencecenter.octopus.files.Files#size(nl.esciencecenter.octopus.files.AbsolutePath)
     */
    @Override
    public long size(AbsolutePath path) throws OctopusIOException {
        
        if (!exists(path)) {
            throw new NoSuchFileException(LocalAdaptor.ADAPTOR_NAME, "File " + path.toString() + " does not exist!");
        }
        
        if (isDirectory(path) || isSymbolicLink(path)) { 
            return 0;
        }
        
        try { 
            return Files.size(LocalUtils.javaPath(path));
        } catch (IOException e) { 
            throw new OctopusIOException(LocalAdaptor.ADAPTOR_NAME, "Failed to retrieve size of " + path.toString(), e);
        }
    }

}
