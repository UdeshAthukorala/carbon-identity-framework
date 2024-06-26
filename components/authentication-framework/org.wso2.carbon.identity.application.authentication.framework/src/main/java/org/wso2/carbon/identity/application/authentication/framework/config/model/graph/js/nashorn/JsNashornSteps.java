/*
 *  Copyright (c) 2018, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.identity.application.authentication.framework.config.model.graph.js.nashorn;

import org.wso2.carbon.identity.application.authentication.framework.config.model.graph.js.JsSteps;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;

import java.util.Objects;

/**
 * Returns when context.steps[step_number] is called
 * This wrapper uses jdk.nashorn engine.
 */
public class JsNashornSteps extends JsSteps implements AbstractJsObject {

    public JsNashornSteps() {

        super();

    }

    public JsNashornSteps(AuthenticationContext context) {

        super(context);
    }

    @Override
    public Object getSlot(int step) {

        Object jsStep = super.getSlot(step);
        return Objects.nonNull(jsStep) ? jsStep : AbstractJsObject.super.getSlot(step);
    }
}
