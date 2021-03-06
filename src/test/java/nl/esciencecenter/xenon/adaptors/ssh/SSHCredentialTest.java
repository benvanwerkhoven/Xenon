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

package nl.esciencecenter.xenon.adaptors.ssh;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import nl.esciencecenter.xenon.adaptors.GenericCredentialsAdaptorTestParent;

/**
 * Test creating SSH credentials.
 * 
 * @author Jason Maassen <J.Maassen@esciencecenter.nl>
 * @version 1.0
 * @since 1.0
 *
 */
public class SSHCredentialTest extends GenericCredentialsAdaptorTestParent {

    @BeforeClass
    public static void prepareLocalFileAdaptorTest() throws Exception {
        GenericCredentialsAdaptorTestParent.prepareClass(new SSHCredentialTestConfig());
    }
    
    @Ignore("'Correct' certificate file from config requires reading files, so it is an integration test") @Test
    @Override
    public void test00_newCertificateCredential_OK() throws Exception {
    }
    
    @Ignore("Default credentials require reading in files, so it is an integration test") @Test
    @Override
    public void test06_newDefaultCredential() throws Exception {
    }
}
