/*
*  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.databridge.agent.test.binary;

import junit.framework.Assert;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wso2.carbon.databridge.agent.AgentHolder;
import org.wso2.carbon.databridge.agent.DataPublisher;
import org.wso2.carbon.databridge.agent.exception.DataEndpointAgentConfigurationException;
import org.wso2.carbon.databridge.agent.exception.DataEndpointAuthenticationException;
import org.wso2.carbon.databridge.agent.exception.DataEndpointConfigurationException;
import org.wso2.carbon.databridge.agent.exception.DataEndpointException;
import org.wso2.carbon.databridge.agent.test.DataPublisherTestUtil;
import org.wso2.carbon.databridge.commons.Event;
import org.wso2.carbon.databridge.commons.exception.MalformedStreamDefinitionException;
import org.wso2.carbon.databridge.commons.exception.TransportException;
import org.wso2.carbon.databridge.commons.utils.DataBridgeCommonsUtils;
import org.wso2.carbon.databridge.core.exception.DataBridgeException;
import org.wso2.carbon.databridge.core.exception.StreamDefinitionStoreException;

import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;


public class OneEndPointDPSyncBinaryTest {
    Log log = LogFactory.getLog(OneEndPointDPSyncBinaryTest.class);
    private static final String STREAM_NAME = "org.wso2.esb.MediatorStatistics";
    private static final String VERSION = "1.0.0";
    private BinaryTestServer testServer;
    private String agentConfigFileName = "sync-data-agent-config.xml";


    private static final String STREAM_DEFN = "{" +
            "  'name':'" + STREAM_NAME + "'," +
            "  'version':'" + VERSION + "'," +
            "  'nickName': 'Stock Quote Information'," +
            "  'description': 'Some Desc'," +
            "  'tags':['foo', 'bar']," +
            "  'metaData':[" +
            "          {'name':'ipAdd','type':'STRING'}" +
            "  ]," +
            "  'payloadData':[" +
            "          {'name':'symbol','type':'STRING'}," +
            "          {'name':'price','type':'DOUBLE'}," +
            "          {'name':'volume','type':'INT'}," +
            "          {'name':'max','type':'DOUBLE'}," +
            "          {'name':'min','type':'Double'}" +
            "  ]" +
            "}";


    @BeforeClass
    public static void init() {
        DataPublisherTestUtil.setKeyStoreParams();
        DataPublisherTestUtil.setTrustStoreParams();
    }


    @AfterClass
    public static void shop() throws DataEndpointAuthenticationException, DataEndpointAgentConfigurationException, TransportException, DataEndpointException, DataEndpointConfigurationException {
        DataPublisher dataPublisher = new DataPublisher("Binary", "tcp://localhost:9687",
                "ssl://localhost:9787", "admin", "admin");
        dataPublisher.shutdownWithAgent();
    }


    private synchronized void startServer(int port, int securePort) throws DataBridgeException,
            StreamDefinitionStoreException, MalformedStreamDefinitionException, IOException {
        testServer = new BinaryTestServer();
        testServer.start(port, securePort);
        testServer.addStreamDefinition(STREAM_DEFN, -1234);
    }

    @Test
    public void testOneDataEndpoint() throws DataEndpointAuthenticationException, DataEndpointAgentConfigurationException, TransportException, DataEndpointException, DataEndpointConfigurationException, MalformedStreamDefinitionException, DataBridgeException, StreamDefinitionStoreException, IOException {
        startServer(9682, 9782);
        AgentHolder.setConfigPath(DataPublisherTestUtil.getDataAgentConfigPath(agentConfigFileName));
        String hostName = DataPublisherTestUtil.LOCAL_HOST;
        DataPublisher dataPublisher = new DataPublisher("Binary", "tcp://" + hostName + ":9682",
                "ssl://" + hostName + ":9782", "admin", "admin");
        Event event = new Event();
        event.setStreamId(DataBridgeCommonsUtils.generateStreamId(STREAM_NAME, VERSION));
        event.setMetaData(new Object[]{"127.0.0.1"});
        event.setCorrelationData(null);
        event.setPayloadData(new Object[]{"WSO2", 123.4, 2, 12.4, 1.3});

        int numberOfEventsSent = 1000;
        for (int i = 0; i < numberOfEventsSent; i++) {
            dataPublisher.publish(event);
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
        }
        dataPublisher.shutdown();
        Assert.assertEquals(numberOfEventsSent, testServer.getNumberOfEventsReceived());
        testServer.resetReceivedEvents();
        testServer.stop();
    }

    @Test
    public void testOneDataEndpointWithArbitraryEventFields() throws DataEndpointAuthenticationException, DataEndpointAgentConfigurationException, TransportException, DataEndpointException, DataEndpointConfigurationException, MalformedStreamDefinitionException, DataBridgeException, StreamDefinitionStoreException, IOException {
        startServer(9602, 9702);
        AgentHolder.setConfigPath(DataPublisherTestUtil.getDataAgentConfigPath(agentConfigFileName));
        String hostName = DataPublisherTestUtil.LOCAL_HOST;
        DataPublisher dataPublisher = new DataPublisher("Binary", "tcp://" + hostName + ":9602",
                "ssl://" + hostName + ":9702", "admin", "admin");
        Event event = new Event();
        event.setStreamId(DataBridgeCommonsUtils.generateStreamId(STREAM_NAME, VERSION));
        event.setMetaData(new Object[]{"127.0.0.1"});
        event.setCorrelationData(null);
        event.setPayloadData(new Object[]{"WSO2", 123.4, 2, 12.4, 1.3});
        Map<String, String> arbitrary = new HashMap<String, String>();
        arbitrary.put("test", "testValue");
        arbitrary.put("test1", "test123");
        event.setArbitraryDataMap(arbitrary);

        int numberOfEventsSent = 1000;
        for (int i = 0; i < numberOfEventsSent; i++) {
            dataPublisher.publish(event);
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
        }
        dataPublisher.shutdown();
        Assert.assertEquals(numberOfEventsSent, testServer.getNumberOfEventsReceived());
        testServer.resetReceivedEvents();
        testServer.stop();
    }

    @Test
    public void testTwoDataEndpoint() throws DataEndpointAuthenticationException,
            DataEndpointAgentConfigurationException, TransportException,
            DataEndpointException, DataEndpointConfigurationException,
            MalformedStreamDefinitionException, DataBridgeException,
            StreamDefinitionStoreException, IOException {
        startServer(9623, 9723);
        AgentHolder.setConfigPath(DataPublisherTestUtil.getDataAgentConfigPath(agentConfigFileName));
        String hostName = DataPublisherTestUtil.LOCAL_HOST;
        DataPublisher dataPublisher = new DataPublisher("Binary", "tcp://" + hostName + ":9623, tcp://" + hostName + ":9622",
                "ssl://" + hostName + ":9723, ssl://" + hostName + ":9722", "admin", "admin");
        Event event = new Event();
        event.setStreamId(DataBridgeCommonsUtils.generateStreamId(STREAM_NAME, VERSION));
        event.setMetaData(new Object[]{"127.0.0.1"});
        event.setCorrelationData(null);
        event.setPayloadData(new Object[]{"WSO2", 123.4, 2, 12.4, 1.3});
        int numberOfEventsSent = 1000;
        for (int i = 0; i < numberOfEventsSent; i++) {
            dataPublisher.publish(event);
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
        }
        dataPublisher.shutdown();
        Assert.assertEquals(numberOfEventsSent, testServer.getNumberOfEventsReceived());
        testServer.resetReceivedEvents();
        testServer.stop();
    }

    @Test
    public void testInvalidAuthenticationURLs() throws DataEndpointAuthenticationException, DataEndpointAgentConfigurationException, TransportException, DataEndpointException, DataEndpointConfigurationException, MalformedStreamDefinitionException, DataBridgeException, StreamDefinitionStoreException, SocketException {
        boolean expected = false;
        DataPublisherTestUtil.setKeyStoreParams();
        DataPublisherTestUtil.setTrustStoreParams();
        AgentHolder.setConfigPath(DataPublisherTestUtil.getDataAgentConfigPath(agentConfigFileName));
        String hostName = DataPublisherTestUtil.LOCAL_HOST;
        try {
            DataPublisher dataPublisher = new DataPublisher("Binary", "tcp://" + hostName + ":9611, ssl://" + hostName + ":9731",
                    "ssl://" + hostName + ":9711", "admin", "admin");

        } catch (DataEndpointConfigurationException ex) {
            expected = true;
        }
        Assert.assertTrue("Invalid urls passed for receiver and auth, and hence expected to fail", expected);
    }

    @Test
    public void testInvalidReceiverURLs() throws DataEndpointAuthenticationException,
            DataEndpointAgentConfigurationException, TransportException,
            DataEndpointException, DataEndpointConfigurationException,
            MalformedStreamDefinitionException,
            DataBridgeException,
            StreamDefinitionStoreException, SocketException {
        boolean expected = false;
        DataPublisherTestUtil.setKeyStoreParams();
        DataPublisherTestUtil.setTrustStoreParams();
        AgentHolder.setConfigPath(DataPublisherTestUtil.getDataAgentConfigPath(agentConfigFileName));
        String hostName = DataPublisherTestUtil.LOCAL_HOST;
        try {
            DataPublisher dataPublisher = new DataPublisher("Binary", "tcp://" + hostName + ":9611",
                    "ssl://" + hostName + ":9711, ssl://" + hostName + ":9721", "admin", "admin");
        } catch (DataEndpointConfigurationException ex) {
            expected = true;
        }
        Assert.assertTrue("Invalid urls passed for receiver and auth, and hence expected to fail", expected);
    }

    @Test
    public void testShutdownDataPublisher() throws DataEndpointAuthenticationException,
            DataEndpointAgentConfigurationException, TransportException,
            DataEndpointException, DataEndpointConfigurationException,
            MalformedStreamDefinitionException, DataBridgeException,
            StreamDefinitionStoreException, IOException {
        startServer(9642, 9742);
        AgentHolder.setConfigPath(DataPublisherTestUtil.getDataAgentConfigPath(agentConfigFileName));
        String hostName = DataPublisherTestUtil.LOCAL_HOST;
        DataPublisher dataPublisher = new DataPublisher("Binary", "tcp://" + hostName + ":9642",
                "ssl://" + hostName + ":9742", "admin", "admin");
        Event event = new Event();
        event.setStreamId(DataBridgeCommonsUtils.generateStreamId(STREAM_NAME, VERSION));
        event.setMetaData(new Object[]{"127.0.0.1"});
        event.setCorrelationData(null);
        event.setPayloadData(new Object[]{"WSO2", 123.4, 2, 12.4, 1.3});

        int numberOfEventsSent = 100000;
        for (int i = 0; i < numberOfEventsSent; i++) {
            dataPublisher.publish(event);
        }

        dataPublisher.shutdown();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
        }
        Assert.assertEquals(numberOfEventsSent, testServer.getNumberOfEventsReceived());
        testServer.resetReceivedEvents();
        testServer.stop();
    }

}
