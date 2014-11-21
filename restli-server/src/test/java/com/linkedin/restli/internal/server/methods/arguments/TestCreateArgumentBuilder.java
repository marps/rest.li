/*
   Copyright (c) 2014 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package com.linkedin.restli.internal.server.methods.arguments;

import com.linkedin.data.template.DataTemplateUtil;
import com.linkedin.r2.message.rest.RestRequest;
import com.linkedin.restli.common.test.MyComplexKey;
import com.linkedin.restli.internal.server.RoutingResult;
import com.linkedin.restli.internal.server.model.AnnotationSet;
import com.linkedin.restli.internal.server.model.Parameter;
import com.linkedin.restli.internal.server.model.ResourceMethodDescriptor;
import com.linkedin.restli.internal.server.model.ResourceModel;
import com.linkedin.restli.internal.server.util.RestLiSyntaxException;
import com.linkedin.restli.server.ResourceContext;
import com.linkedin.restli.server.RestLiRequestData;
import org.easymock.EasyMock;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.annotation.Annotation;


/**
 * @author Soojung Ha
 */
public class TestCreateArgumentBuilder
{
  @Test
  public void testArgumentBuilder() throws RestLiSyntaxException
  {
    RestRequest request = RestLiArgumentBuilderTestHelper.getMockRequest(false, "{\"a\":\"xyz\",\"b\":123}", 1);
    ResourceModel model = RestLiArgumentBuilderTestHelper.getMockResourceModel(MyComplexKey.class, null, false);
    Parameter<MyComplexKey> param = new Parameter<MyComplexKey>("",
        MyComplexKey.class,
        DataTemplateUtil.getSchema(MyComplexKey.class),
        false,
        null,
        Parameter.ParamType.POST,
        false,
        new AnnotationSet(new Annotation[]{}));
    ResourceMethodDescriptor descriptor = RestLiArgumentBuilderTestHelper.getMockResourceMethodDescriptor(model, param);
    ResourceContext context = RestLiArgumentBuilderTestHelper.getMockResourceContext(null, null, null);
    RoutingResult routingResult = RestLiArgumentBuilderTestHelper.getMockRoutingResult(descriptor, 2, context, 1);

    RestLiArgumentBuilder argumentBuilder = new CreateArgumentBuilder();
    RestLiRequestData requestData = argumentBuilder.extractRequestData(routingResult, request);
    Object[] args = argumentBuilder.buildArguments(requestData, routingResult);
    Assert.assertEquals(args.length, 1);
    Assert.assertTrue(args[0] instanceof MyComplexKey);
    Assert.assertEquals(((MyComplexKey)args[0]).getA(), "xyz");
    Assert.assertEquals((long) ((MyComplexKey)args[0]).getB(), 123L);

    EasyMock.verify(request, model, descriptor, context, routingResult);
  }
}