/*
 *  Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.databridge.agent.util;

import java.io.File;

/**
 * Duplicated the code from org.wso2.carbon.utils.CarbonUtils with necessary methods to remove carbon core dependency.
 */
public class CarbonUtils {

    private static final String CARBON_CONFIG_DIR_PATH = "carbon.config.dir.path";
    private static final String CARBON_CONFIG_DIR_PATH_ENV = "CARBON_CONFIG_DIR_PATH";
    private static final String REPOSITORY = "repository";
    private static final String CARBON_HOME = "carbon.home";
    private static final String CARBON_HOME_ENV = "CARBON_HOME";

    public static String getCarbonConfigDirPath() {

        String carbonConfigDirPath = System.getProperty(CARBON_CONFIG_DIR_PATH);
        if (carbonConfigDirPath == null) {
            carbonConfigDirPath = System.getenv(CARBON_CONFIG_DIR_PATH_ENV);
            if (carbonConfigDirPath == null) {
                return getCarbonHome() + File.separator + REPOSITORY + File.separator + "conf";
            }
        }
        return carbonConfigDirPath;
    }

    private static String getCarbonHome() {

        String carbonHome = System.getProperty(CARBON_HOME);
        if (carbonHome == null) {
            carbonHome = System.getenv(CARBON_HOME_ENV);
            System.setProperty(CARBON_HOME, carbonHome);
        }
        return carbonHome;
    }

}
