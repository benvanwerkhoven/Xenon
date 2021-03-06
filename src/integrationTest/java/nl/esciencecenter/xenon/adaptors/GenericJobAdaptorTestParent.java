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

package nl.esciencecenter.xenon.adaptors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import nl.esciencecenter.xenon.InvalidCredentialException;
import nl.esciencecenter.xenon.InvalidLocationException;
import nl.esciencecenter.xenon.InvalidPropertyException;
import nl.esciencecenter.xenon.UnknownPropertyException;
import nl.esciencecenter.xenon.Xenon;
import nl.esciencecenter.xenon.XenonException;
import nl.esciencecenter.xenon.XenonFactory;
import nl.esciencecenter.xenon.XenonTestWatcher;
import nl.esciencecenter.xenon.credentials.Credential;
import nl.esciencecenter.xenon.credentials.Credentials;
import nl.esciencecenter.xenon.files.Files;
import nl.esciencecenter.xenon.files.Path;
import nl.esciencecenter.xenon.jobs.Job;
import nl.esciencecenter.xenon.jobs.JobStatus;
import nl.esciencecenter.xenon.jobs.Jobs;
import nl.esciencecenter.xenon.jobs.NoSuchQueueException;
import nl.esciencecenter.xenon.jobs.NoSuchSchedulerException;
import nl.esciencecenter.xenon.jobs.QueueStatus;
import nl.esciencecenter.xenon.jobs.Scheduler;
import nl.esciencecenter.xenon.util.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jason Maassen <J.Maassen@esciencecenter.nl>
 * 
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class GenericJobAdaptorTestParent {
    private static final Logger logger = LoggerFactory.getLogger(GenericJobAdaptorTestParent.class);

    private static String TEST_ROOT;

    protected static JobTestConfig config;

    protected Xenon xenon;
    protected Files files;
    protected Jobs jobs;
    protected Credentials credentials;

    @Rule
    public TestWatcher watcher = new XenonTestWatcher();
    
    public Path resolve(Path root, String path) throws XenonException { 
        return files.newPath(root.getFileSystem(), root.getRelativePath().resolve(path));
    }
    
    // MUST be invoked by a @BeforeClass method of the subclass! 
    public static void prepareClass(JobTestConfig testConfig) {
        config = testConfig;
        TEST_ROOT = "xenon_test_" + config.getAdaptorName() + "_" + System.currentTimeMillis();
    }

    // MUST be invoked by a @AfterClass method of the subclass! 
    public static void cleanupClass() throws Exception {
        logger.info("GenericJobAdaptorTest.cleanupClass() attempting to remove: " + TEST_ROOT);

        Xenon xenon = XenonFactory.newXenon(null);

        Files files = xenon.files();
        Credentials credentials = xenon.credentials();

        Path cwd = config.getWorkingDir(files, credentials);
        Path root = files.newPath(cwd.getFileSystem(), cwd.getRelativePath().resolve(TEST_ROOT));

        if (files.exists(root)) {
            Utils.recursiveDelete(files, root);
        }

        XenonFactory.endXenon(xenon);
    }

    @Before
    public void prepare() throws Exception {
        // This is not an adaptor option, so it will throw an exception!
        //Map<String, String> properties = new HashMap<>();
        //properties.put(SshAdaptor.POLLING_DELAY, "100");
        xenon = XenonFactory.newXenon(null);
        files = xenon.files();
        jobs = xenon.jobs();
        credentials = xenon.credentials();
    }

    @After
    public void cleanup() throws XenonException {
        // XenonFactory.endXenon(xenon);
        XenonFactory.endAll();
    }

    protected String getWorkingDir(String testName) {
        return TEST_ROOT + "/" + testName;
    }

    // TEST: newScheduler
    //
    // location: null / valid URI / invalid URI 
    // credential: null / default / set / wrong
    // properties: null / empty / set / wrong

    @Test(expected = XenonException.class)
    public void test00_newScheduler() throws Exception {
        jobs.newScheduler(null, null, null, null);
    }

    @Test
    public void test01a_newScheduler() throws Exception {
        if (!config.supportsNullLocation()) {
            try { 
                Scheduler s = jobs.newScheduler(config.getScheme(), null, null, null);
                jobs.close(s);
                fail("Expected InvalidLocationException");                
            } catch (InvalidLocationException e) { 
                // expected
            }
        }
    }

    @Test
    public void test02a_newScheduler() throws Exception {
        Scheduler s = jobs.newScheduler(config.getScheme(), config.getCorrectLocation(), null, null);
        jobs.close(s);
    }
    
    @Test(expected = XenonException.class)
    public void test02b_newScheduler() throws Exception {
        jobs.newScheduler(config.getScheme(), config.getWrongLocation(), null, null);
    }

    @Test
    public void test03_newScheduler() throws Exception {
        Scheduler s = jobs.newScheduler(config.getScheme(), config.getCorrectLocation(), config.getDefaultCredential(credentials),
                null);
        jobs.close(s);
    }

    @Test
    public void test04a_newScheduler() throws Exception {
        if (config.supportsCredentials()) {
            try {
                Scheduler s = jobs.newScheduler(config.getScheme(), config.getCorrectLocation(), 
                        config.getInvalidCredential(credentials), null);
                
                jobs.close(s);
                throw new Exception("newScheduler did NOT throw InvalidCredentialsException");
            } catch (InvalidCredentialException e) {
                // expected
            } catch (XenonException e) {
                // allowed
            }
        }
    }

    @Test
    public void test04b_newScheduler() throws Exception {
        if (!config.supportsCredentials()) {
            try {
                Credential c = new Credential() {
                    @Override
                    public Map<String, String> getProperties() {
                        return null;
                    }

                    @Override
                    public String getAdaptorName() {
                        return "local";
                    }
                };

                Scheduler s = jobs.newScheduler(config.getScheme(), config.getCorrectLocation(), c, null);
                jobs.close(s);

                throw new Exception("newScheduler did NOT throw XenonException");
            } catch (XenonException e) {
                // expected
            }
        }
    }

    @Test
    public void test04c_newScheduler() throws Exception {
        if (config.supportsCredentials()) {
            Scheduler s = jobs.newScheduler(config.getScheme(), config.getCorrectLocation(), 
                    config.getPasswordCredential(credentials), config.getDefaultProperties());
            jobs.close(s);
        }
    }

    @Test
    public void test05_newScheduler() throws Exception {
        Scheduler s = jobs.newScheduler(config.getScheme(), config.getCorrectLocation(), 
                config.getDefaultCredential(credentials), new HashMap<String, String>());
        jobs.close(s);
    }

    @Test
    public void test06_newScheduler() throws Exception {
        Scheduler s = jobs.newScheduler(config.getScheme(), config.getCorrectLocation(), 
                config.getDefaultCredential(credentials), config.getDefaultProperties());
        jobs.close(s);
    }

    @Test
    public void test07_newScheduler() throws Exception {
        if (config.supportsProperties()) {

            Map<String, String>[] tmp = config.getInvalidProperties();

            for (Map<String, String> p : tmp) {
                try {
                    Scheduler s = jobs.newScheduler(config.getScheme(), config.getCorrectLocation(), 
                            config.getDefaultCredential(credentials), p);
                    jobs.close(s);
                    throw new Exception("newScheduler did NOT throw InvalidPropertyException");
                } catch (InvalidPropertyException e) {
                    // expected
                }
            }
        }
    }

    @Test
    public void test08_newScheduler() throws Exception {
        if (config.supportsProperties()) {
            try {
                Scheduler s = jobs.newScheduler(config.getScheme(), config.getCorrectLocation(), 
                        config.getDefaultCredential(credentials), config.getUnknownProperties());
                jobs.close(s);

                throw new Exception("newScheduler did NOT throw UnknownPropertyException");
            } catch (UnknownPropertyException e) {
                // expected
            }
        }
    }

    @Test
    public void test09_newScheduler() throws Exception {
        if (!config.supportsProperties()) {
            try {
                Map<String, String> p = new HashMap<>(2);
                p.put("aap", "noot");
                Scheduler s = jobs.newScheduler(config.getScheme(), config.getCorrectLocation(), 
                        config.getDefaultCredential(credentials), p);
                jobs.close(s);

                throw new Exception("newScheduler did NOT throw XenonException");
            } catch (XenonException e) {
                // expected
            }
        }
    }

    @Test
    public void test10_getLocalScheduler() throws Exception {
        Scheduler s = null;

        try {
            s = jobs.newScheduler("local", null, null, null);
            assertNotNull(s);
            assertEquals("local", s.getAdaptorName());
        } finally {
            if (s != null) {
                jobs.close(s);
            }
        }
    }

    @Test
    public void test11_open_close() throws Exception {
        if (config.supportsClose()) {
            Scheduler s = config.getDefaultScheduler(jobs, credentials);

            assertTrue(jobs.isOpen(s));

            jobs.close(s);

            assertFalse(jobs.isOpen(s));
        }
    }

    @Test
    public void test12_open_close() throws Exception {
        if (!config.supportsClose()) {
            Scheduler s = config.getDefaultScheduler(jobs, credentials);

            assertTrue(jobs.isOpen(s));

            jobs.close(s);

            assertTrue(jobs.isOpen(s));
        }
    }

    @Test
    public void test13_open_close() throws Exception {
        if (config.supportsClose()) {
            Scheduler s = config.getDefaultScheduler(jobs, credentials);
            jobs.close(s);

            try {
                jobs.close(s);
                fail("close did NOT throw NoSuchSchedulerException");
            } catch (NoSuchSchedulerException e) {
                // expected
            }
        }
    }

    @Test
    public void test14a_getJobs() throws Exception {
        Scheduler s = config.getDefaultScheduler(jobs, credentials);
        jobs.getJobs(s, s.getQueueNames());
        jobs.close(s);
    }

    @Test
    public void test14b_getJobs() throws Exception {
        Scheduler s = config.getDefaultScheduler(jobs, credentials);
        jobs.getJobs(s);
        jobs.close(s);
    }

    @Test
    public void test15_getJobs() throws Exception {
        Scheduler s = config.getDefaultScheduler(jobs, credentials);

        try {
            jobs.getJobs(s, config.getInvalidQueueName());
            fail("getJobs did NOT throw NoSuchQueueException");
        } catch (NoSuchQueueException e) {
            // expected
        } finally {
            jobs.close(s);
        }
    }

    @Test
    public void test16_getJobs() throws Exception {

        if (config.supportsClose()) {

            Scheduler s = config.getDefaultScheduler(jobs, credentials);

            jobs.close(s);

            try {
                jobs.getJobs(s, s.getQueueNames());
                fail("close did NOT throw NoSuchSchedulerException");
            } catch (NoSuchSchedulerException e) {
                // expected
            }
        }
    }

    @Test
    public void test17_getQueueStatus() throws Exception {
        Scheduler s = config.getDefaultScheduler(jobs, credentials);
        jobs.getQueueStatus(s, s.getQueueNames()[0]);
        jobs.close(s);
    }

    @Test(expected = NoSuchQueueException.class)
    public void test18a_getQueueStatus() throws Exception {
        Scheduler s = config.getDefaultScheduler(jobs, credentials);
        try {
            jobs.getQueueStatus(s, config.getInvalidQueueName());
        } finally {
            jobs.close(s);
        }
    }

    @Test
    public void test18b_getQueueStatus() throws Exception {
        Scheduler s = config.getDefaultScheduler(jobs, credentials);
        String queueName = config.getDefaultQueueName();

        try {
            jobs.getQueueStatus(s, queueName);
        } finally {
            jobs.close(s);
        }
    }

    @Test(expected = NullPointerException.class)
    public void test19_getQueueStatus() throws Exception {
        jobs.getQueueStatus(null, null);
    }

    @Test
    public void test20_getQueueStatus() throws Exception {
        if (config.supportsClose()) {
            Scheduler s = config.getDefaultScheduler(jobs, credentials);
            jobs.close(s);

            try {
                jobs.getQueueStatus(s, s.getQueueNames()[0]);
                fail("getQueueStatus did NOT throw NoSuchSchedulerException");
            } catch (NoSuchSchedulerException e) {
                // expected
            }
        }
    }

    @Test
    public void test21a_getQueueStatuses() throws Exception {
        Scheduler s = config.getDefaultScheduler(jobs, credentials);
        QueueStatus[] tmp = jobs.getQueueStatuses(s, s.getQueueNames());
        jobs.close(s);

        String[] names = s.getQueueNames();

        assertNotNull(tmp);
        assertEquals(tmp.length, names.length);

        for (int i = 0; i < tmp.length; i++) {
            assertEquals(names[i], tmp[i].getQueueName());
        }
    }

    @Test
    public void test21b_getQueueStatuses() throws Exception {
        Scheduler s = config.getDefaultScheduler(jobs, credentials);
        QueueStatus[] tmp = jobs.getQueueStatuses(s);
        jobs.close(s);

        String[] names = s.getQueueNames();

        assertNotNull(tmp);
        assertEquals(names.length, tmp.length);

        for (int i = 0; i < tmp.length; i++) {
            assertEquals(names[i], tmp[i].getQueueName());
        }

    }

    @Test
    public void test22a_getQueueStatuses() throws Exception {
        Scheduler s = config.getDefaultScheduler(jobs, credentials);
        try {
            QueueStatus[] tmp = jobs.getQueueStatuses(s, config.getInvalidQueueName());

            assertNotNull(tmp);
            assertEquals(1, tmp.length);
            assertTrue(tmp[0].hasException());
            assertTrue(tmp[0].getException() instanceof NoSuchQueueException);
        } finally {
            jobs.close(s);
        }
    }

    @Test(expected = NullPointerException.class)
    public void test22b_getQueueStatuses() throws Exception {
        jobs.getQueueStatuses(null, config.getDefaultQueueName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void test22c_getQueueStatuses() throws Exception {
        Scheduler s = config.getDefaultScheduler(jobs, credentials);
        jobs.getQueueStatuses(s, (String[]) null);
    }

    @Test(expected = NullPointerException.class)
    public void test23_getQueueStatuses() throws Exception {
        jobs.getQueueStatuses(null);
    }

    @Test
    public void test24_getQueueStatuses() throws Exception {

        if (config.supportsClose()) {
            Scheduler s = config.getDefaultScheduler(jobs, credentials);
            jobs.close(s);

            try {
                jobs.getQueueStatuses(s, s.getQueueNames());
                throw new Exception("getQueueStatuses did NOT throw NoSuchSchedulerException");
            } catch (NoSuchSchedulerException e) {
                // expected
            }
        }
    }

    @Test
    public void test25a_getJobStatuses() throws Exception {

        JobStatus[] tmp = jobs.getJobStatuses(new Job[0]);

        assertNotNull(tmp);
        assertEquals(0, tmp.length);
    }

    @Test
    public void test25b_getJobStatuses() throws Exception {

        JobStatus[] tmp = jobs.getJobStatuses((Job[]) null);

        assertNotNull(tmp);
        assertEquals(0, tmp.length);
    }

    @Test
    public void test25c_getJobStatuses() throws Exception {

        JobStatus[] tmp = jobs.getJobStatuses(new Job[1]);

        assertNotNull(tmp);
        assertEquals(1, tmp.length);
        assertNull(tmp[0]);
    }
}
