/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.apippi.security;

import javax.net.ssl.SSLException;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.apippi.config.Config;
import org.apache.apippi.config.DatabaseDescriptor;
import org.apache.apippi.exceptions.ConfigurationException;

public class PEMBasedSslContextFactoryInvalidConfigTest
{
    @BeforeClass
    public static void setupDatabaseDescriptor()
    {
        System.setProperty("apippi.config", "apippi-pem-sslcontextfactory-invalidconfiguration.yaml");
    }

    @AfterClass
    public static void tearDownDatabaseDescriptor()
    {
        System.clearProperty("apippi.config");
    }

    @Test(expected = ConfigurationException.class)
    public void testFileBasedAndInlinePEMConfiguration() throws SSLException
    {

        Config config = DatabaseDescriptor.loadConfig();
        config.client_encryption_options.applyConfig();

        Assert.assertEquals("org.apache.apippi.security.PEMBasedSslContextFactory",
                            config.client_encryption_options.ssl_context_factory.class_name);
        Assert.assertEquals(config.client_encryption_options.ssl_context_factory.class_name,
                            config.client_encryption_options.sslContextFactoryInstance.getClass().getName());
        PEMBasedSslContextFactory sslContextFactory =
        (PEMBasedSslContextFactory) config.client_encryption_options.sslContextFactoryInstance;
        sslContextFactory.buildKeyManagerFactory();
        sslContextFactory.buildTrustManagerFactory();
    }
}
