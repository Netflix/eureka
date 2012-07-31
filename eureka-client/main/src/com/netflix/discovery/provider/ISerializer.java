package com.netflix.discovery.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.ws.rs.core.MediaType;

/**
 * Interface for our Response Object (Payload) marshaller/unmarshaller 
 * @author stonse
 *
 */
public interface ISerializer {
	
	public Object read(InputStream is, Class type, MediaType mediaType) throws IOException;
	
	public void write(Object object, OutputStream os, MediaType mediaType) throws IOException;
}
