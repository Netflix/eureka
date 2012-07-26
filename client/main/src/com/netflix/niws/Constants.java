package com.netflix.niws;

/**
 * 
 * @author stonse
 *
 */
public final class Constants {

	//pays to keep the header name slower-case
	//based on experience, some web servers have the nasty habit of lowercasing these
	public static final String HEADER_PAYLOAD_CONVERTER = "x-netflix-niws-payloadconverter";
	public static final String HEADER_PAYLOAD_ENTITY_CLASS = "x-netflix-niws-payload-entityclass";
	
	public static final String HTTP_ACCEPT = "Accept";
	
	public static final String CONTENT_TYPE_APPLICATION_XML = "application/xml";
	public static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";
	public static final String CONTENT_TYPE_APPLICATION_PROTOBUF = "application/x-protobuf";
	public static final String CONTENT_TYPE_APPLICATION_OCTET_STREAM = "application/octet-stream";
	public static final String CONTENT_TYPE_TEXT_JSON = "text/json";
	public static final String CONTENT_TYPE = "Content-Type";
	public static final String REQUEST_SAMPLING_PROP_NAME = "platform.request.sampling.percent";
	
	//Avro headers
	public static final String AVRO_SCHEMA = "Avro-Schema";
	
}
