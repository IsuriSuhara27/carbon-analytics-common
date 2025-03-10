/*
 * Copyright 2004,2005 The Apache Software Foundation.
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
package org.wso2.carbon.databridge.core.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.databridge.commons.StreamDefinition;
import org.wso2.carbon.databridge.commons.exception.DifferentStreamDefinitionAlreadyDefinedException;
import org.wso2.carbon.databridge.commons.exception.MalformedStreamDefinitionException;
import org.wso2.carbon.databridge.commons.utils.EventDefinitionConverterUtils;
import org.wso2.carbon.databridge.core.DataBridge;
import org.wso2.carbon.databridge.core.DataBridgeReceiverService;
import org.wso2.carbon.databridge.core.DataBridgeServiceValueHolder;
import org.wso2.carbon.databridge.core.DataBridgeSubscriberService;
import org.wso2.carbon.databridge.core.definitionstore.AbstractStreamDefinitionStore;
import org.wso2.carbon.databridge.core.exception.StreamDefinitionStoreException;
import org.wso2.carbon.databridge.core.internal.authentication.CarbonAuthenticationHandler;
import org.wso2.carbon.databridge.core.internal.utils.DataBridgeCoreBuilder;
import org.wso2.carbon.identity.authentication.AuthenticationService;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.ConfigurationContextService;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import java.util.List;

@Component(
        name = "databridge.component",
        immediate = true)
public class DataBridgeDS {

    private static final Log log = LogFactory.getLog(DataBridgeDS.class);

    private AuthenticationService authenticationService;

    private ServiceRegistration receiverServiceRegistration;

    private ServiceRegistration subscriberServiceRegistration;

    private DataBridge databridge;

    private ServiceRegistration databridgeRegistration;

    /**
     * initialize the agent server here.
     *
     * @param context
     */
    @Activate
    protected void activate(ComponentContext context) {

        try {
            if (databridge == null) {
                AbstractStreamDefinitionStore streamDefinitionStore = DataBridgeServiceValueHolder
                        .getStreamDefinitionStore();
                databridge = new DataBridge(new CarbonAuthenticationHandler(authenticationService),
                        streamDefinitionStore, DataBridgeCoreBuilder.getDatabridgeConfigPath());
                try {
                    List<String[]> streamDefinitionStrings = DataBridgeCoreBuilder.loadStreamDefinitionXML();
                    for (String[] streamDefinitionString : streamDefinitionStrings) {
                        try {
                            StreamDefinition streamDefinition = EventDefinitionConverterUtils.convertFromJson
                                    (streamDefinitionString[1]);
                            int tenantId = DataBridgeServiceValueHolder.getRealmService().getTenantManager()
                                    .getTenantId(streamDefinitionString[0]);
                            if (tenantId == MultitenantConstants.INVALID_TENANT_ID) {
                                log.warn("Tenant " + streamDefinitionString[0] + " does not exist, Error in defining " +
                                        "event stream " + streamDefinitionString[1]);
                                continue;
                            }
                            try {
                                PrivilegedCarbonContext.startTenantFlow();
                                PrivilegedCarbonContext privilegedCarbonContext = PrivilegedCarbonContext
                                        .getThreadLocalCarbonContext();
                                privilegedCarbonContext.setTenantId(tenantId);
                                privilegedCarbonContext.setTenantDomain(streamDefinitionString[0]);
                                streamDefinitionStore.saveStreamDefinition(streamDefinition, tenantId);
                            } catch (DifferentStreamDefinitionAlreadyDefinedException e) {
                                log.warn("Error redefining event stream of " + streamDefinitionString[0] + ": " +
                                        streamDefinitionString[1], e);
                            } catch (RuntimeException e) {
                                log.error("Error in defining event stream " + streamDefinitionString[0] + ": " +
                                        streamDefinitionString[1], e);
                            } catch (StreamDefinitionStoreException e) {
                                log.error("Error in defining event stream in store " + streamDefinitionString[0] + ":" +
                                        " " + streamDefinitionString[1], e);
                            } finally {
                                PrivilegedCarbonContext.endTenantFlow();
                            }
                        } catch (MalformedStreamDefinitionException e) {
                            log.error("Malformed Stream Definition for " + streamDefinitionString[0] + ": " +
                                    streamDefinitionString[1], e);
                        } catch (UserStoreException e) {
                            log.error("Error in identifying tenant event stream " + streamDefinitionString[0] + ": "
                                    + streamDefinitionString[1], e);
                        }
                    }
                } catch (Throwable t) {
                    log.error("Cannot load stream definitions ", t);
                }
                receiverServiceRegistration = context.getBundleContext().registerService(DataBridgeReceiverService
                        .class.getName(), databridge, null);
                subscriberServiceRegistration = context.getBundleContext().registerService
                        (DataBridgeSubscriberService.class.getName(), databridge, null);
                // databridgeRegistration =
                // context.getBundleContext().registerService(DataBridge.class.getName(), databridge, null);
                log.info("Successfully deployed Agent Server ");
            }
        } catch (RuntimeException e) {
            log.error("Error in starting Agent Server ", e);
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {

        context.getBundleContext().ungetService(receiverServiceRegistration.getReference());
        context.getBundleContext().ungetService(subscriberServiceRegistration.getReference());
        // databridgeRegistration.unregister();
        if (log.isDebugEnabled()) {
            log.debug("Successfully stopped agent server");
        }
    }

    @Reference(
            name = "org.wso2.carbon.identity.authentication.internal.AuthenticationServiceComponent",
            service = org.wso2.carbon.identity.authentication.AuthenticationService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetAuthenticationService")
    protected void setAuthenticationService(AuthenticationService authenticationService) {

        this.authenticationService = authenticationService;
    }

    protected void unsetAuthenticationService(AuthenticationService authenticationService) {

        this.authenticationService = null;
    }

    @Reference(
            name = "user.realmservice.default",
            service = org.wso2.carbon.user.core.service.RealmService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetRealmService")
    protected void setRealmService(RealmService realmService) {

        DataBridgeServiceValueHolder.setRealmService(realmService);
    }

    protected void unsetRealmService(RealmService realmService) {

        DataBridgeServiceValueHolder.setRealmService(null);
    }

    @Reference(
            name = "stream.definitionStore.service",
            service = org.wso2.carbon.databridge.core.definitionstore.AbstractStreamDefinitionStore.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetEventStreamStoreService")
    protected void setEventStreamStoreService(AbstractStreamDefinitionStore abstractStreamDefinitionStore) {

        DataBridgeServiceValueHolder.setStreamDefinitionStore(abstractStreamDefinitionStore);
    }

    protected void unsetEventStreamStoreService(AbstractStreamDefinitionStore abstractStreamDefinitionStore) {

        DataBridgeServiceValueHolder.setStreamDefinitionStore(null);
    }

    @Reference(
            name = "config.context.service",
            service = org.wso2.carbon.utils.ConfigurationContextService.class,
            cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetConfigurationContextService")
    protected void setConfigurationContextService(ConfigurationContextService contextService) {

        DataBridgeServiceValueHolder.setConfigurationContextService(contextService);
    }

    protected void unsetConfigurationContextService(ConfigurationContextService contextService) {

        DataBridgeServiceValueHolder.setConfigurationContextService(null);
    }
}
