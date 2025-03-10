/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.event.input.adapter.websocket.internal.ds;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.wso2.carbon.event.input.adapter.core.InputEventAdapterFactory;
import org.wso2.carbon.event.input.adapter.websocket.WebsocketEventAdapterFactory;

@Component(
        name = "input.websocketEventAdapterService.component",
        immediate = true)
public class WebsocketEventAdapterServiceDS {

    private static final Log log = LogFactory.getLog(WebsocketEventAdapterServiceDS.class);

    /**
     * initialize the agent service here service here.
     *
     * @param context
     */
    @Activate
    protected void activate(ComponentContext context) {

        try {
            InputEventAdapterFactory websocketEventAdapterFactory = new WebsocketEventAdapterFactory();
            context.getBundleContext().registerService(InputEventAdapterFactory.class.getName(),
                    websocketEventAdapterFactory, null);
            if (log.isDebugEnabled()) {
                log.debug("Successfully deployed the input websocket adapter service");
            }
        } catch (RuntimeException e) {
            log.error("Can not create the input websocket adapter service ", e);
        }
    }
}
