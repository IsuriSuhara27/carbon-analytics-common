/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.event.template.manager.admin.internal.ds;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.carbon.event.template.manager.core.TemplateManagerService;

/**
 * This class is used to get the EventProcessor service.
 */
@Component(
        name = "org.wso2.carbon.event.template.manager.admin.TemplateManagerAdminService",
        immediate = true)
public class TemplateManagerAdminServiceDS {

    /**
     * Will be invoked when activating the service
     *
     * @param context
     */
    @Activate
    protected void activate(ComponentContext context) {

    }

    @Reference(
            name = "templateManagerService.service",
            service = org.wso2.carbon.event.template.manager.core.TemplateManagerService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetTemplateManagerService")
    protected void setTemplateManagerService(TemplateManagerService templateManagerService) {

        TemplateManagerAdminServiceValueHolder.setTemplateManagerService(templateManagerService);
    }

    protected void unsetTemplateManagerService(TemplateManagerService templateManagerService) {

        TemplateManagerAdminServiceValueHolder.setTemplateManagerService(null);
    }
}
