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
package org.wso2.carbon.event.output.adapter.email;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.event.output.adapter.core.MessageType;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapter;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapterConfiguration;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapterFactory;
import org.wso2.carbon.event.output.adapter.core.Property;
import org.wso2.carbon.event.output.adapter.email.internal.util.EmailEventAdapterConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * The email event adapter factory class to create an email output adapter
 */
public class EmailEventAdapterFactory extends OutputEventAdapterFactory {
    private ResourceBundle resourceBundle =
            ResourceBundle.getBundle("org.wso2.carbon.event.output.adapter.email.i18n.Resources", Locale.getDefault());


    private static final Log log = LogFactory.getLog(EmailEventAdapterFactory.class);

    @Override
    public String getType() {
        return EmailEventAdapterConstants.ADAPTER_TYPE_EMAIL;
    }

    @Override
    public List<String> getSupportedMessageFormats() {
        List<String> supportedMessageFormats = new ArrayList<String>();
        supportedMessageFormats.add(MessageType.TEXT);
        supportedMessageFormats.add(MessageType.XML);
        supportedMessageFormats.add(MessageType.JSON);
        return supportedMessageFormats;
    }

    @Override
    public List<Property> getStaticPropertyList() {

        List<Property> staticpropertyList = new ArrayList<>();

        Property smtplUserName = new Property(EmailEventAdapterConstants.ADAPTER_EMAIL_SMTP_USER);
        smtplUserName.setDisplayName(resourceBundle.getString(EmailEventAdapterConstants.ADAPTER_EMAIL_SMTP_USER));
        smtplUserName.setRequired(false);

        Property smtpPassword = new Property(EmailEventAdapterConstants.ADAPTER_EMAIL_SMTP_PASSWORD);
        smtpPassword.setDisplayName(resourceBundle.getString(EmailEventAdapterConstants.ADAPTER_EMAIL_SMTP_PASSWORD));
        smtpPassword.setRequired(false);

        Property smtpAuth = new Property(EmailEventAdapterConstants.ADAPTER_EMAIL_SMTP_AUTH);
        smtpAuth.setDisplayName(resourceBundle.getString(EmailEventAdapterConstants.ADAPTER_EMAIL_SMTP_AUTH));
        smtpAuth.setRequired(false);

        Property smtpFrom = new Property(EmailEventAdapterConstants.ADAPTER_EMAIL_SMTP_FROM);
        smtpFrom.setDisplayName(resourceBundle.getString(EmailEventAdapterConstants.ADAPTER_EMAIL_SMTP_FROM));
        smtpFrom.setRequired(false);

        Property smtpHost = new Property(EmailEventAdapterConstants.ADAPTER_EMAIL_SMTP_HOST);
        smtpHost.setDisplayName(resourceBundle.getString(EmailEventAdapterConstants.ADAPTER_EMAIL_SMTP_HOST));
        smtpHost.setRequired(false);

        Property smtpPort = new Property(EmailEventAdapterConstants.ADAPTER_EMAIL_SMTP_PORT);
        smtpPort.setDisplayName(resourceBundle.getString(EmailEventAdapterConstants.ADAPTER_EMAIL_SMTP_PORT));
        smtpPort.setRequired(false);

        Property startTLSEnable = new Property(EmailEventAdapterConstants.ADAPTER_EMAIL_SMTP_STARTTLS_ENABLE);
        startTLSEnable.setDisplayName(
                resourceBundle.getString(EmailEventAdapterConstants.ADAPTER_EMAIL_SMTP_STARTTLS_ENABLE));
        startTLSEnable.setRequired(false);

        Property senderSignature = new Property(EmailEventAdapterConstants.MAIL_SMTP_SIGNATURE);
        startTLSEnable.setDisplayName(resourceBundle.getString(EmailEventAdapterConstants.MAIL_SMTP_SIGNATURE));
        startTLSEnable.setRequired(false);

        Property replyToAddress = new Property(EmailEventAdapterConstants.MAIL_SMTP_REPLY_TO);
        startTLSEnable.setDisplayName(resourceBundle.getString(EmailEventAdapterConstants.MAIL_SMTP_REPLY_TO));
        startTLSEnable.setRequired(false);

        staticpropertyList.add(smtplUserName);
        staticpropertyList.add(smtpPassword);
        staticpropertyList.add(smtpAuth);
        staticpropertyList.add(smtpFrom);
        staticpropertyList.add(smtpHost);
        staticpropertyList.add(smtpPort);
        staticpropertyList.add(startTLSEnable);
        staticpropertyList.add(senderSignature);
        staticpropertyList.add(replyToAddress);

        return staticpropertyList;
    }

    @Override
    public List<Property> getDynamicPropertyList() {
        List<Property> dynamicPropertyList = new ArrayList<Property>();

        // set email address
        Property emailAddress = new Property(EmailEventAdapterConstants.ADAPTER_MESSAGE_EMAIL_ADDRESS);
        emailAddress.setDisplayName(
                resourceBundle.getString(EmailEventAdapterConstants.ADAPTER_MESSAGE_EMAIL_ADDRESS));
        emailAddress.setRequired(true);
        emailAddress.setHint(resourceBundle.getString(EmailEventAdapterConstants.ADAPTER_MESSAGE_EMAIL_ADDRESS_HINT));

        // set email subject
        Property subject = new Property(EmailEventAdapterConstants.ADAPTER_MESSAGE_EMAIL_SUBJECT);
        subject.setDisplayName(
                resourceBundle.getString(EmailEventAdapterConstants.ADAPTER_MESSAGE_EMAIL_SUBJECT));
        subject.setRequired(true);


        //set format of the email
        Property format = new Property(EmailEventAdapterConstants.APAPTER_MESSAGE_EMAIL_TYPE);
        format.setDisplayName
                (resourceBundle.getString(EmailEventAdapterConstants.APAPTER_MESSAGE_EMAIL_TYPE));
        format.setRequired(false);
        format.setOptions(new String[]{EmailEventAdapterConstants.MAIL_TEXT_PLAIN, EmailEventAdapterConstants.MAIL_TEXT_HTML});
        format.setDefaultValue(EmailEventAdapterConstants.MAIL_TEXT_PLAIN);
        format.setHint(resourceBundle.getString(EmailEventAdapterConstants.ADAPTER_MESSAGE_EMAIL_TYPE_HINT));


        dynamicPropertyList.add(emailAddress);
        dynamicPropertyList.add(subject);
        dynamicPropertyList.add(format);

        return dynamicPropertyList;
    }

    @Override
    public String getUsageTips() {
        return null;
    }

    @Override
    public OutputEventAdapter createEventAdapter(OutputEventAdapterConfiguration eventAdapterConfiguration, Map<String,
            String> globalProperties) {
        return new EmailEventAdapter(eventAdapterConfiguration, globalProperties);

    }
}
