package com.adaptris.vertx;

import com.adaptris.core.SerializableAdaptrisMessage;
import com.adaptris.core.ServiceCollection;
import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("vertx-message")
public class VertXMessage {
  
  private SerializableAdaptrisMessage adaptrisMessage;
    
  private ServiceRecord serviceRecord;
  
  private long startProcessingTime;
  
  public VertXMessage() {
    serviceRecord = new ServiceRecord();
  }

  public VertXMessage(SerializableAdaptrisMessage adaptrisMessage, ServiceCollection serviceCollection) {
    this.setAdaptrisMessage(adaptrisMessage);
    this.setServiceRecord(new ServiceRecord(serviceCollection));
  }
  
  public String getNextServiceID() throws ServiceRecordException {
    InterlokService nextService = this.getServiceRecord().getNextService();
    if(nextService != null)
      return nextService.getId();
    else
      return null;
  }
  
  public SerializableAdaptrisMessage getAdaptrisMessage() {
    return adaptrisMessage;
  }

  public void setAdaptrisMessage(SerializableAdaptrisMessage adaptrisMessage) {
    this.adaptrisMessage = adaptrisMessage;
  }

  public ServiceRecord getServiceRecord() {
    return serviceRecord;
  }

  public void setServiceRecord(ServiceRecord serviceRecord) {
    this.serviceRecord = serviceRecord;
  }

  public long getStartProcessingTime() {
    return startProcessingTime;
  }

  public void setStartProcessingTime(long startProcessingTime) {
    this.startProcessingTime = startProcessingTime;
  }
  
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("Start processing at :" + this.getStartProcessingTime());
    builder.append("\n");
    builder.append("Service Record :\n" + this.getServiceRecord());
    builder.append("\n");
    builder.append("\n");
    return builder.toString();
  }

}
