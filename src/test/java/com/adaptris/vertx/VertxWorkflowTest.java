package com.adaptris.vertx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageProducerImp;
import com.adaptris.core.Channel;
import com.adaptris.core.CoreException;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.core.ProcessingExceptionHandler;
import com.adaptris.core.ProduceException;
import com.adaptris.core.Service;
import com.adaptris.core.ServiceException;
import com.adaptris.core.WorkflowImp;
import com.adaptris.core.common.ConstantDataInputParameter;
import com.adaptris.core.services.LogMessageService;
import com.adaptris.core.stubs.MockChannel;
import com.adaptris.core.stubs.MockNonStandardRequestReplyProducer;
import com.adaptris.core.util.LifecycleHelper;
import com.adaptris.interlok.InterlokException;
import com.adaptris.interlok.junit.scaffolding.ExampleWorkflowCase;
import com.adaptris.interlok.types.InterlokMessage;
import com.adaptris.util.TimeInterval;

import io.vertx.core.eventbus.Message;

public class VertxWorkflowTest extends ExampleWorkflowCase {

  public static final String BASE_DIR_KEY = "WorkflowCase.baseDir";

  private VertxWorkflow vertxWorkflow;

  private ConstantDataInputParameter targetWorkflowId;

  private Channel channel;

  @Mock
  private ConstantDataInputParameter mockTargetWorkflowId;
  @Mock
  private ClusteredEventBus mockClusteredEventBus;
  @Mock
  private ProcessingExceptionHandler mockErrorHandler;
  @Mock
  private VertXMessageTranslator mockTranslator;
  @Mock
  private Message<VertXMessage> mockVertxMessage;
  @Mock
  private Message<Object> mockReplyVertxMessage;
  @Mock
  private Service mockService1, mockService2;
  @Mock
  private AdaptrisMessageProducerImp mockProducer;
  @Mock
  private ArrayBlockingQueue<VertXMessage> mockInternalprocessingQueue;

  private AutoCloseable closeable;

  @After
  public void tearDown() throws Exception {
    LifecycleHelper.stopAndClose(channel);
    closeable.close();
  }

  @Before
  public void setUp() throws Exception {
    closeable = MockitoAnnotations.openMocks(this);

    channel = new MockChannel();

    vertxWorkflow = new VertxWorkflow();
    targetWorkflowId = new ConstantDataInputParameter("SomeWorkflowID");

    vertxWorkflow.setTargetComponentId(targetWorkflowId);
    vertxWorkflow.setProducer(mockProducer);
    vertxWorkflow.setClusteredEventBus(mockClusteredEventBus);
    vertxWorkflow.setMaxThreads(5);
    vertxWorkflow.setVertxProperties(null);
    // INTERLOK-1563 need to invoke the consumerStarted() method, to handle the countdownLatch
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) {
        ((ConsumerEventListener) invocation.getArguments()[0]).consumerStarted();
        return null;
      }
    }).when(mockClusteredEventBus).startClusteredConsumer(vertxWorkflow, null);

    channel.getWorkflowList().add(vertxWorkflow);
    LifecycleHelper.initAndStart(channel);
  }

  @Test
  public void testInitWithNegativeQueueCapacity() throws Exception {
    vertxWorkflow.setQueueCapacity(-1);

    try {
      vertxWorkflow.initialiseWorkflow();
      fail("Expect a core exception, with a negative queue capacity");
    } catch (CoreException ex) {
      // expected
    }
  }

  @Test
  public void testOnMessageInterrupted() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();

    doThrow(new InterruptedException("GeneratedExpected"))
    .when(mockInternalprocessingQueue).put(any(VertXMessage.class));

    vertxWorkflow.setProcessingQueue(mockInternalprocessingQueue);
    vertxWorkflow.registerActiveMsgErrorHandler(mockErrorHandler);

    vertxWorkflow.onAdaptrisMessage(adaptrisMessage);

    verify(mockErrorHandler).handleProcessingException(any(AdaptrisMessage.class));
  }

  @Test
  public void testOnMessageSendToCluster() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();

    vertxWorkflow.onAdaptrisMessage(adaptrisMessage);
  }

  @Test
  public void testOnMessageCannotTranslateMessage() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();

    vertxWorkflow.registerActiveMsgErrorHandler(mockErrorHandler);

    vertxWorkflow.setVertXMessageTranslator(mockTranslator);
    when(mockTranslator.translate(adaptrisMessage))
    .thenThrow(new CoreException("GeneratedException"));

    vertxWorkflow.onAdaptrisMessage(adaptrisMessage);

    verify(mockErrorHandler).handleProcessingException(adaptrisMessage);
  }

  @Test
  public void testOnMessageInterruptedException() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();

    vertxWorkflow.registerActiveMsgErrorHandler(mockErrorHandler);
    vertxWorkflow.setProcessingQueue(mockInternalprocessingQueue);

    doThrow(new InterruptedException("GeneratedException"))
    .when(mockInternalprocessingQueue).put(any(VertXMessage.class));

    vertxWorkflow.onAdaptrisMessage(adaptrisMessage);

    verify(mockErrorHandler).handleProcessingException(adaptrisMessage);
  }

  @Test
  public void testReceivedVertxMessage() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    VertXMessage vertXMessage = new VertXMessageTranslator().translate(adaptrisMessage);

    when(mockVertxMessage.body())
    .thenReturn(vertXMessage);

    vertxWorkflow.onVertxMessage(mockVertxMessage);

    verify(mockVertxMessage).reply(vertXMessage);
  }

  @Test
  public void testReceivedHandleVertxMessage() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    VertXMessage vertXMessage = new VertXMessageTranslator().translate(adaptrisMessage);

    when(mockVertxMessage.body())
    .thenReturn(vertXMessage);

    vertxWorkflow.handle(mockVertxMessage);

    Thread.sleep(500l);
    verify(mockVertxMessage).reply(vertXMessage);
  }

  @Test
  public void testReceivedVertxMessageFailsTranslate() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    VertXMessage vertXMessage = new VertXMessageTranslator().translate(adaptrisMessage);

    vertxWorkflow.setVertXMessageTranslator(mockTranslator);

    when(mockVertxMessage.body())
    .thenReturn(vertXMessage);
    when(mockTranslator.translate(vertXMessage))
    .thenThrow(new CoreException("GeneratedException"));

    vertxWorkflow.onVertxMessage(mockVertxMessage);

    verify(mockVertxMessage, never()).reply(vertXMessage);
  }

  @Test
  public void testReceivedVertxMessageFailsTranslateWhenReplying() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    VertXMessage vertXMessage = new VertXMessageTranslator().translate(adaptrisMessage);

    vertxWorkflow.setVertXMessageTranslator(mockTranslator);

    when(mockVertxMessage.body())
    .thenReturn(vertXMessage);
    when(mockTranslator.translate(vertXMessage))
    .thenReturn(adaptrisMessage);
    when(mockTranslator.translate(any(AdaptrisMessage.class)))
    .thenThrow(new CoreException("GeneratedException"));

    vertxWorkflow.onVertxMessage(mockVertxMessage);

    verify(mockVertxMessage, never()).reply(vertXMessage);
  }

  @Test
  public void testReceivedVertxMessageRunsServices() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    VertXMessage vertXMessage = new VertXMessageTranslator().translate(adaptrisMessage);

    when(mockVertxMessage.body())
    .thenReturn(vertXMessage);

    vertxWorkflow.getServiceCollection().add(mockService1);
    vertxWorkflow.getServiceCollection().add(mockService2);

    vertxWorkflow.onVertxMessage(mockVertxMessage);

    verify(mockService1).doService(any());
    verify(mockService2).doService(any());
    verify(mockVertxMessage).reply(vertXMessage);
  }

  @Test
  public void testReceivedVertxMessageRunsServicesFirstServiceError() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    VertXMessage vertXMessage = new VertXMessageTranslator().translate(adaptrisMessage);

    when(mockVertxMessage.body())
    .thenReturn(vertXMessage);

    doThrow(new ServiceException("GeneratedException"))
    .when(mockService1).doService(any());

    vertxWorkflow.getServiceCollection().add(mockService1);
    vertxWorkflow.getServiceCollection().add(mockService2);

    vertxWorkflow.onVertxMessage(mockVertxMessage);

    verify(mockService1).doService(any());
    verify(mockService2, never()).doService(any());
    verify(mockVertxMessage).reply(vertXMessage);
  }

  @Test
  public void testReceivedVertxMessageRunsServicesFirstServiceErrorContinueOnError() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    VertXMessage vertXMessage = new VertXMessageTranslator().translate(adaptrisMessage);

    when(mockVertxMessage.body())
    .thenReturn(vertXMessage);

    doThrow(new ServiceException("GeneratedException"))
    .when(mockService1).doService(any());

    vertxWorkflow.getServiceCollection().add(mockService1);
    vertxWorkflow.getServiceCollection().add(mockService2);

    vertxWorkflow.setContinueOnError(true);

    vertxWorkflow.onVertxMessage(mockVertxMessage);

    verify(mockService1).doService(any());
    verify(mockService2).doService(any());
    verify(mockVertxMessage).reply(vertXMessage);
  }

  @Test
  public void testOnMessageTargetFails() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    VertXMessage vertXMessage = new VertXMessageTranslator().translate(adaptrisMessage);

    ArrayBlockingQueue<VertXMessage> internalprocessingQueue = new ArrayBlockingQueue<>(1);
    internalprocessingQueue.put(vertXMessage);

    vertxWorkflow.registerActiveMsgErrorHandler(mockErrorHandler);
    vertxWorkflow.setProcessingQueue(internalprocessingQueue);
    vertxWorkflow.setTargetComponentId(mockTargetWorkflowId);

    when(mockTargetWorkflowId.extract(any(InterlokMessage.class)))
    .thenThrow(new InterlokException("GeneratedException"));

    vertxWorkflow.processQueuedMessage();

    verify(mockErrorHandler).handleProcessingException(any(AdaptrisMessage.class));
  }

  @Test
  public void testOnMessageSendToSingle() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    VertXMessage vertXMessage = new VertXMessageTranslator().translate(adaptrisMessage);

    ArrayBlockingQueue<VertXMessage> internalprocessingQueue = new ArrayBlockingQueue<>(1);
    internalprocessingQueue.put(vertXMessage);

    vertxWorkflow.setProcessingQueue(internalprocessingQueue);

    vertxWorkflow.processQueuedMessage();

    verify(mockClusteredEventBus).send(any(), any(), anyBoolean());
  }

  @Test
  public void testOnMessageSendToAll() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    VertXMessage vertXMessage = new VertXMessageTranslator().translate(adaptrisMessage);

    ArrayBlockingQueue<VertXMessage> internalprocessingQueue = new ArrayBlockingQueue<>(1);
    internalprocessingQueue.put(vertXMessage);

    vertxWorkflow.setTargetSendMode(SendMode.Mode.ALL);
    vertxWorkflow.setProcessingQueue(internalprocessingQueue);

    vertxWorkflow.processQueuedMessage();

    verify(mockClusteredEventBus).publish(any(), any());
  }

  @Test
  public void testHandleMessageReplyFailsTranslator() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    VertXMessage vertXMessage = new VertXMessageTranslator().translate(adaptrisMessage);

    vertxWorkflow.registerActiveMsgErrorHandler(mockErrorHandler);
    vertxWorkflow.setVertXMessageTranslator(mockTranslator);

    when(mockReplyVertxMessage.body())
    .thenReturn(vertXMessage);
    when(mockTranslator.translate(vertXMessage))
    .thenThrow(new CoreException("GeneratedException"));

    vertxWorkflow.handleMessageReply(mockReplyVertxMessage);

    verify(mockProducer, never()).produce(adaptrisMessage);
  }

  @Test
  public void testHandleMessageReplyHasErrors() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    VertXMessage vertXMessage = new VertXMessageTranslator().translate(adaptrisMessage);
    vertXMessage.getServiceRecord().addService(new InterlokService("SomeId", ServiceState.ERROR));

    vertxWorkflow.registerActiveMsgErrorHandler(mockErrorHandler);

    when(mockReplyVertxMessage.body())
    .thenReturn(vertXMessage);

    vertxWorkflow.handleMessageReply(mockReplyVertxMessage);

    verify(mockErrorHandler).handleProcessingException(any(AdaptrisMessage.class));
  }

  @Test
  public void testHandleMessageReplyDoesProduce() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    VertXMessage vertXMessage = new VertXMessageTranslator().translate(adaptrisMessage);
    vertXMessage.getServiceRecord().addService(new InterlokService("SomeId", ServiceState.COMPLETE));

    when(mockReplyVertxMessage.body())
    .thenReturn(vertXMessage);
    when(mockProducer.createName())
    .thenReturn("name");

    vertxWorkflow.handleMessageReply(mockReplyVertxMessage);

    verify(mockProducer).produce(any(AdaptrisMessage.class));
  }

  @Test
  public void testHandleMessageReplyProduceFails() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    VertXMessage vertXMessage = new VertXMessageTranslator().translate(adaptrisMessage);
    vertXMessage.getServiceRecord().addService(new InterlokService("SomeId", ServiceState.COMPLETE));

    vertxWorkflow.registerActiveMsgErrorHandler(mockErrorHandler);

    when(mockReplyVertxMessage.body())
    .thenReturn(vertXMessage);
    when(mockProducer.createName())
    .thenReturn("name");
    doThrow(new ProduceException("Generated Exception"))
    .when(mockProducer).produce(any(AdaptrisMessage.class));

    vertxWorkflow.handleMessageReply(mockReplyVertxMessage);

    verify(mockProducer).produce(any(AdaptrisMessage.class));
    verify(mockErrorHandler).handleProcessingException(any(AdaptrisMessage.class));
  }

  @Test
  public void testObjectMetadataCopy() throws Exception {
    AdaptrisMessage adaptrisMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    adaptrisMessage.addObjectHeader("ObjectHeaderKey", "ObjectHeaderValue");

    MockNonStandardRequestReplyProducer producer = new MockNonStandardRequestReplyProducer();
    vertxWorkflow.setProducer(producer);

    vertxWorkflow.setProcessingQueue(mockInternalprocessingQueue);

    vertxWorkflow.onAdaptrisMessage(adaptrisMessage);

    VertXMessage vertXMessage = new VertXMessageTranslator().translate(adaptrisMessage);
    vertXMessage.getServiceRecord().addService(new InterlokService("SomeId", ServiceState.COMPLETE));

    when(mockReplyVertxMessage.body())
    .thenReturn(vertXMessage);
    when(mockProducer.createName())
    .thenReturn("name");

    vertxWorkflow.handleMessageReply(mockReplyVertxMessage);

    AdaptrisMessage message = (AdaptrisMessage) producer.getProducedMessages().get(0);
    assertEquals("ObjectHeaderValue", message.getObjectHeaders().get("ObjectHeaderKey"));
  }

  @Override
  protected WorkflowImp createWorkflowForGenericTests() throws Exception {
    VertxWorkflow workflow = new VertxWorkflow();
    workflow.setUniqueId("my-cluster-name");
    workflow.setTargetComponentId(new ConstantDataInputParameter("my-cluster-name"));
    workflow.getServiceCollection().add(new LogMessageService());
    return workflow;
  }

  @Override
  protected Object retrieveObjectForSampleConfig() {
    Channel channel = new Channel();
    channel.setUniqueId("my-channel-id");
    VertxWorkflow workflow = new VertxWorkflow();
    workflow.setUniqueId("my-cluster-name");
    workflow.setTargetComponentId(new ConstantDataInputParameter("my-cluster-name"));
    workflow.getServiceCollection().add(new LogMessageService());
    workflow.setItemExpiryTimeout(new TimeInterval(30L, TimeUnit.SECONDS));

    channel.getWorkflowList().add(workflow);
    return channel;
  }

}
