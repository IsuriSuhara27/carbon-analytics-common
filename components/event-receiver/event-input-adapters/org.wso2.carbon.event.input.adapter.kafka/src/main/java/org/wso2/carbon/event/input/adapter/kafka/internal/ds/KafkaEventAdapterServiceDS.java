/*
 * Copyright (c) 2005 - 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.wso2.carbon.event.input.adapter.kafka.internal.ds;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.carbon.event.input.adapter.core.InputEventAdapterFactory;
import org.wso2.carbon.event.input.adapter.kafka.KafkaEventAdapterFactory;
import org.wso2.carbon.utils.ConfigurationContextService;

/**
 */
@Component(
        name = "input.Kafka.EventAdaptorService.component",
        immediate = true)
public class KafkaEventAdapterServiceDS {

    private static final Log log = LogFactory.getLog(KafkaEventAdapterServiceDS.class);

    /**
     * initialize the agent service here service here.
     *
     * @param context
     */
    @Activate
    protected void activate(ComponentContext context) {

        try {
            InputEventAdapterFactory kafkaEventEventAdapterFactory = new KafkaEventAdapterFactory();
            context.getBundleContext().registerService(InputEventAdapterFactory.class.getName(),
                    kafkaEventEventAdapterFactory, null);
            if (log.isDebugEnabled()) {
                log.debug("Successfully deployed the input Kafka adapter service");
            }
        } catch (RuntimeException e) {
            log.error("Can not create the input Kafka adapter service ", e);
        }
    }

    @Reference(
            name = "configurationcontext.service",
            service = org.wso2.carbon.utils.ConfigurationContextService.class,
            cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetConfigurationContextService")
    protected void setConfigurationContextService(ConfigurationContextService configurationContextService) {

        KafkaEventAdapterServiceHolder.registerConfigurationContextService(configurationContextService);
    }

    protected void unsetConfigurationContextService(ConfigurationContextService configurationContextService) {

        KafkaEventAdapterServiceHolder.unregisterConfigurationContextService(configurationContextService);
    }
}
