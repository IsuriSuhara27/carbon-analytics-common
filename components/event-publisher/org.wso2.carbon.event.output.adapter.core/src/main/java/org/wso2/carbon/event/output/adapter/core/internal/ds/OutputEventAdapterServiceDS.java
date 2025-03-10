/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.event.output.adapter.core.internal.ds;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapterFactory;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapterService;
import org.wso2.carbon.event.output.adapter.core.internal.CarbonOutputEventAdapterService;
import org.wso2.carbon.event.output.adapter.core.internal.util.EventAdapterConfigHelper;
import org.wso2.carbon.securevault.SecretCallbackHandlerService;
import org.wso2.carbon.utils.ConfigurationContextService;

import java.util.ArrayList;
import java.util.List;

@Component(
        name = "event.output.adapter.service",
        immediate = true)
public class OutputEventAdapterServiceDS {

    private static final Log log = LogFactory.getLog(OutputEventAdapterServiceDS.class);

    private static final List<OutputEventAdapterFactory> outputEventAdapterFactories = new
            ArrayList<OutputEventAdapterFactory>();

    /**
     * initialize the Event Adapter Manager core service here.
     *
     * @param context the component context that will be passed in from the OSGi environment at activation
     */
    @Activate
    protected void activate(ComponentContext context) {

        OutputEventAdapterServiceValueHolder.setGlobalAdapterConfigs(EventAdapterConfigHelper.loadGlobalConfigs());
        CarbonOutputEventAdapterService outputEventAdapterService = new CarbonOutputEventAdapterService();
        OutputEventAdapterServiceValueHolder.setCarbonOutputEventAdapterService(outputEventAdapterService);
        registerOutputEventAdapterFactories();
        context.getBundleContext().registerService(OutputEventAdapterService.class.getName(),
                outputEventAdapterService, null);
        try {
            if (log.isDebugEnabled()) {
                log.debug("Successfully deployed the output event adapter service");
            }
        } catch (RuntimeException e) {
            log.error("Can not create the output event adapter service ", e);
        }
    }

    private void registerOutputEventAdapterFactories() {

        CarbonOutputEventAdapterService carbonOutputEventAdapterService = OutputEventAdapterServiceValueHolder
                .getCarbonOutputEventAdapterService();
        for (OutputEventAdapterFactory outputEventAdapterFactory : outputEventAdapterFactories) {
            carbonOutputEventAdapterService.registerEventAdapterFactory(outputEventAdapterFactory);
        }
    }

    @Reference(
            name = "output.event.adapter.tracker.service",
            service = org.wso2.carbon.event.output.adapter.core.OutputEventAdapterFactory.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unSetEventAdapterType")
    protected void setEventAdapterType(OutputEventAdapterFactory outputEventAdapterFactory) {

        try {
            if (OutputEventAdapterServiceValueHolder.getCarbonOutputEventAdapterService() != null) {
                OutputEventAdapterServiceValueHolder.getCarbonOutputEventAdapterService().registerEventAdapterFactory
                        (outputEventAdapterFactory);
                // OutputEventAdapterServiceValueHolder.getCarbonEventPublisherService()
                // .activateInactiveEventFormatterConfigurationForAdapter(abstractOutputEventAdapter
                // .getOutputEventAdapterDto().getType());
            } else {
                outputEventAdapterFactories.add(outputEventAdapterFactory);
            }
        } catch (Throwable t) {
            String outputEventAdapterFactoryClassName = "Unknown";
            if (outputEventAdapterFactory != null) {
                outputEventAdapterFactoryClassName = outputEventAdapterFactory.getClass().getName();
            }
            log.error("Unexpected error at initializing output event adapter factory " +
                    outputEventAdapterFactoryClassName + ": " + t.getMessage(), t);
        }
    }

    protected void unSetEventAdapterType(OutputEventAdapterFactory outputEventAdapterFactory) {

        OutputEventAdapterServiceValueHolder.getCarbonOutputEventAdapterService().unRegisterEventAdapterFactory
                (outputEventAdapterFactory);
    }

    @Reference(
            name = "config.context.service",
            service = org.wso2.carbon.utils.ConfigurationContextService.class,
            cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetConfigurationContextService")
    protected void setConfigurationContextService(ConfigurationContextService configurationContextService) {

        OutputEventAdapterServiceValueHolder.setConfigurationContextService(configurationContextService);
    }

    protected void unsetConfigurationContextService(ConfigurationContextService configurationContextService) {

        OutputEventAdapterServiceValueHolder.setConfigurationContextService(null);
    }

    @Reference(
            name = "secret.callback.handler.service",
            service = org.wso2.carbon.securevault.SecretCallbackHandlerService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetSecretCallbackHandlerService")
    protected void setSecretCallbackHandlerService(SecretCallbackHandlerService secretCallbackHandlerService) {

        OutputEventAdapterServiceValueHolder.setSecretCallbackHandlerService(secretCallbackHandlerService);
    }

    protected void unsetSecretCallbackHandlerService(SecretCallbackHandlerService secretCallbackHandlerService) {

        OutputEventAdapterServiceValueHolder.setSecretCallbackHandlerService(null);
    }
}
