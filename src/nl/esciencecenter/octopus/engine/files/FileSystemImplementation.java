package nl.esciencecenter.octopus.engine.files;

import java.net.URI;

import nl.esciencecenter.octopus.OctopusProperties;
import nl.esciencecenter.octopus.files.FileSystem;

public class FileSystemImplementation implements FileSystem {

    private final String adaptorName;
    private final String uniqueID; 
    
    private final URI uri;
    private final OctopusProperties properties;
    
    public FileSystemImplementation(String adaptorName, String uniqueID, URI uri, OctopusProperties properties) {
        this.adaptorName = adaptorName;
        this.uniqueID = uniqueID;
        this.uri = uri;
        this.properties = properties;
    }

    @Override
    public String getUniqueID() {
        return uniqueID;
    }
    
    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public OctopusProperties getProperties() {
        return properties;
    }

    @Override
    public String getAdaptorName() {
        return adaptorName;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((adaptorName == null) ? 0 : adaptorName.hashCode());
        result = prime * result + ((uniqueID == null) ? 0 : uniqueID.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FileSystemImplementation other = (FileSystemImplementation) obj;
        if (adaptorName == null) {
            if (other.adaptorName != null)
                return false;
        } else if (!adaptorName.equals(other.adaptorName))
            return false;
        if (uniqueID == null) {
            if (other.uniqueID != null)
                return false;
        } else if (!uniqueID.equals(other.uniqueID))
            return false;
        return true;
    }
    
}