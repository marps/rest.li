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


package com.linkedin.restli.internal.server.response;


import com.linkedin.data.schema.PathSpec;
import com.linkedin.data.schema.StringDataSchema;
import com.linkedin.data.template.InvalidAlternativeKeyException;
import com.linkedin.data.template.KeyCoercer;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.data.transform.filter.request.MaskOperation;
import com.linkedin.data.transform.filter.request.MaskTree;
import com.linkedin.pegasus.generator.examples.Foo;
import com.linkedin.pegasus.generator.examples.Fruits;
import com.linkedin.r2.message.rest.RestRequest;
import com.linkedin.r2.message.rest.RestRequestBuilder;
import com.linkedin.restli.common.CompoundKey;
import com.linkedin.restli.common.HttpStatus;
import com.linkedin.restli.common.IdResponse;
import com.linkedin.restli.common.ProtocolVersion;
import com.linkedin.restli.common.RestConstants;
import com.linkedin.restli.internal.common.AllProtocolVersions;
import com.linkedin.restli.internal.server.RoutingResult;
import com.linkedin.restli.internal.server.ServerResourceContext;
import com.linkedin.restli.internal.server.model.ResourceMethodDescriptor;
import com.linkedin.restli.internal.server.model.ResourceModel;
import com.linkedin.restli.server.AlternativeKey;
import com.linkedin.restli.server.CreateKVResponse;
import com.linkedin.restli.server.CreateResponse;
import com.linkedin.restli.server.ProjectionMode;
import com.linkedin.restli.server.RestLiResponseData;
import com.linkedin.restli.server.RestLiServiceException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.easymock.EasyMock;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


/**
 * @author kparikh
 */
public class TestCreateResponseBuilder
{
  @DataProvider(name = "testData")
  public Object[][] testDataProvider()
  {
    CompoundKey compoundKey = new CompoundKey().append("a", "a").append("b", 1);
    Map<String, AlternativeKey<?, ?>> alternativeKeyMap = new HashMap<String, AlternativeKey<?, ?>>();
    alternativeKeyMap.put("alt", new AlternativeKey<String, CompoundKey>(new TestKeyCoercer(), String.class, new StringDataSchema()));
    return new Object[][]
        {
            { AllProtocolVersions.RESTLI_PROTOCOL_1_0_0.getProtocolVersion(), compoundKey, "/foo/a=a&b=1", "a=a&b=1", null, null },
            { AllProtocolVersions.RESTLI_PROTOCOL_2_0_0.getProtocolVersion(), compoundKey, "/foo/(a:a,b:1)", "(a:a,b:1)", null,  null },
            { AllProtocolVersions.RESTLI_PROTOCOL_1_0_0.getProtocolVersion(), "aaxb1", "/foo/aaxb1?altkey=alt", "aaxb1", "alt", alternativeKeyMap },
            { AllProtocolVersions.RESTLI_PROTOCOL_2_0_0.getProtocolVersion(), "aaxb1", "/foo/aaxb1?altkey=alt", "aaxb1", "alt", alternativeKeyMap },
        };
  }

  @Test(dataProvider = "testData")
  public void testBuilder(ProtocolVersion protocolVersion,
                          Object expectedId,
                          String expectedLocation,
                          String expectedHeaderId,
                          String altKeyName,
                          Map<String, AlternativeKey<?, ?>> alternativeKeyMap) throws URISyntaxException
  {
    CompoundKey compoundKey = new CompoundKey().append("a", "a").append("b", 1);
    CreateResponse createResponse = new CreateResponse(compoundKey);
    IdResponse<?> expectedIdResponse = new IdResponse<Object>(expectedId);
    RestRequest restRequest = new RestRequestBuilder(new URI("/foo")).build();
    Map<String, String> headers = ResponseBuilderUtil.getHeaders();
    headers.put(RestConstants.HEADER_RESTLI_PROTOCOL_VERSION, protocolVersion.toString());
    // the headers passed in are modified
    Map<String, String> expectedHeaders = new HashMap<String, String>(headers);

    ResourceMethodDescriptor mockDescriptor = getMockResourceMethodDescriptor(alternativeKeyMap);
    ServerResourceContext mockContext = getMockResourceContext(protocolVersion, altKeyName);
    RoutingResult routingResult = new RoutingResult(mockContext, mockDescriptor);

    CreateResponseBuilder createResponseBuilder = new CreateResponseBuilder();
    RestLiResponseData<CreateResponseEnvelope> responseData = createResponseBuilder.buildRestLiResponseData(restRequest,
                                                                                    routingResult,
                                                                                    createResponse,
                                                                                    headers,
                                                                                    Collections.emptyList());
    Assert.assertFalse(responseData.getResponseEnvelope().isGetAfterCreate());

    PartialRestResponse partialRestResponse = createResponseBuilder.buildResponse(routingResult, responseData);

    expectedHeaders.put(RestConstants.HEADER_LOCATION, expectedLocation);
    if (protocolVersion.equals(AllProtocolVersions.RESTLI_PROTOCOL_1_0_0.getProtocolVersion()))
    {
      expectedHeaders.put(RestConstants.HEADER_ID, expectedHeaderId);
    }
    else
    {
      expectedHeaders.put(RestConstants.HEADER_RESTLI_ID, expectedHeaderId);
    }

    EasyMock.verify(mockContext, mockDescriptor);
    ResponseBuilderUtil.validateHeaders(partialRestResponse, expectedHeaders);
    Assert.assertEquals(partialRestResponse.getStatus(), HttpStatus.S_201_CREATED);
    Assert.assertEquals(partialRestResponse.getEntity(), expectedIdResponse);
  }

  @Test
  public void testCreateResponseException() throws URISyntaxException
  {
    CreateResponse createResponse = new CreateResponse(new RestLiServiceException(HttpStatus.S_400_BAD_REQUEST));
    RestRequest restRequest = new RestRequestBuilder(new URI("/foo")).build();
    RestLiResponseData<?> envelope = new CreateResponseBuilder()
        .buildRestLiResponseData(restRequest, null, createResponse, Collections.emptyMap(),
                                 Collections.emptyList());

    Assert.assertTrue(envelope.getResponseEnvelope().isErrorResponse());
  }

  @Test
  public void testBuilderException()
      throws URISyntaxException
  {
    CompoundKey compoundKey = new CompoundKey().append("a", "a").append("b", 1);
    CreateResponse createResponse = new CreateResponse(compoundKey, null);
    RestRequest restRequest = new RestRequestBuilder(new URI("/foo")).build();
    ProtocolVersion protocolVersion = AllProtocolVersions.RESTLI_PROTOCOL_1_0_0.getProtocolVersion();
    Map<String, String> headers = ResponseBuilderUtil.getHeaders();
    headers.put(RestConstants.HEADER_RESTLI_PROTOCOL_VERSION, protocolVersion.toString());

    ResourceMethodDescriptor mockDescriptor = getMockResourceMethodDescriptor(null);
    ServerResourceContext mockContext = getMockResourceContext(protocolVersion, null);
    RoutingResult routingResult = new RoutingResult(mockContext, mockDescriptor);

    CreateResponseBuilder createResponseBuilder = new CreateResponseBuilder();
    try
    {
      createResponseBuilder.buildRestLiResponseData(restRequest, routingResult, createResponse, headers, Collections.emptyList());
      Assert.fail("buildRestLiResponseData should have thrown an exception because the status is null!");
    }
    catch (RestLiServiceException e)
    {
      Assert.assertTrue(e.getMessage().contains("Unexpected null encountered. HttpStatus is null inside of a CreateResponse from the resource method: "));
    }
  }


  @Test
  public void testProjectionInBuildRestliResponseData() throws URISyntaxException {
    MaskTree maskTree = new MaskTree();
    maskTree.addOperation(new PathSpec("fruitsField"), MaskOperation.POSITIVE_MASK_OP);

    ServerResourceContext mockContext = EasyMock.createMock(ServerResourceContext.class);
    EasyMock.expect(mockContext.getProjectionMask()).andReturn(maskTree);
    EasyMock.expect(mockContext.getProjectionMode()).andReturn(ProjectionMode.AUTOMATIC);
    EasyMock.replay(mockContext);
    RoutingResult routingResult = new RoutingResult(mockContext, null);

    Foo value = new Foo().setStringField("value").setFruitsField(Fruits.APPLE);
    CreateKVResponse<Integer, Foo> values = new CreateKVResponse<>(null, value);

    CreateResponseBuilder responseBuilder = new CreateResponseBuilder();
    RestLiResponseData<CreateResponseEnvelope> envelope = responseBuilder.buildRestLiResponseData(new RestRequestBuilder(new URI("/foo")).build(),
                                                                          routingResult,
                                                                          values,
                                                                          Collections.emptyMap(),
                                                                          Collections.emptyList());
    RecordTemplate record = envelope.getResponseEnvelope().getRecord();
    Assert.assertEquals(record.data().size(), 1);
    Assert.assertEquals(record.data().get("fruitsField"), Fruits.APPLE.toString());
    Assert.assertTrue(envelope.getResponseEnvelope().isGetAfterCreate());

    EasyMock.verify(mockContext);
  }

  private static ServerResourceContext getMockResourceContext(ProtocolVersion protocolVersion,
                                                        String altKeyName)
  {
    ServerResourceContext mockContext = EasyMock.createMock(ServerResourceContext.class);
    EasyMock.expect(mockContext.getRestliProtocolVersion()).andReturn(protocolVersion).once();
    EasyMock.expect(mockContext.hasParameter(RestConstants.ALT_KEY_PARAM)).andReturn(altKeyName != null).atLeastOnce();
    if (altKeyName != null)
    {
      EasyMock.expect(mockContext.getParameter(RestConstants.ALT_KEY_PARAM)).andReturn(altKeyName).atLeastOnce();
    }
    EasyMock.replay(mockContext);
    return mockContext;
  }

  public static ResourceMethodDescriptor getMockResourceMethodDescriptor(Map<String, AlternativeKey<?, ?>> alternativeKeyMap)
  {
    ResourceMethodDescriptor mockDescriptor = EasyMock.createMock(ResourceMethodDescriptor.class);
    if (alternativeKeyMap != null)
    {
      EasyMock.expect(mockDescriptor.getResourceModel()).andReturn(getMockResourceModel(alternativeKeyMap)).atLeastOnce();
    }
    EasyMock.replay(mockDescriptor);
    return mockDescriptor;
  }

  public static ResourceModel getMockResourceModel(Map<String, AlternativeKey<?, ?>> alternativeKeyMap)
  {
    ResourceModel mockResourceModel = EasyMock.createMock(ResourceModel.class);
    EasyMock.expect(mockResourceModel.getAlternativeKeys()).andReturn(alternativeKeyMap).anyTimes();
    EasyMock.replay(mockResourceModel);
    return mockResourceModel;
  }

  private class TestKeyCoercer implements KeyCoercer<String, CompoundKey>
  {
    @Override
    public CompoundKey coerceToKey(String object) throws InvalidAlternativeKeyException
    {
      CompoundKey compoundKey = new CompoundKey();
      compoundKey.append("a", object.substring(1, 2));
      compoundKey.append("b", Integer.parseInt(object.substring(3, 4)));
      return compoundKey;
    }

    @Override
    public String coerceFromKey(CompoundKey object)
    {
      return "a" + object.getPart("a") + "xb" + object.getPart("b");
    }
  }
}
