package com.netflix.niws;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Interface for our Response Object (Payload) marshaller/unmarshaller 
 * @author stonse
 *
 */
public interface IPayloadObjectConverter {
	
	public IPayload read(InputStream is, Class<IPayload> type, String contentType) throws IOException;
	
	public void write(Object object, OutputStream os, String contentType) throws IOException;
}
