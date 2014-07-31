package com.netflix.eureka.transport.avro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.transport.UserContent;
import com.netflix.eureka.transport.UserContentWithAck;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.Schema.Type;
import org.apache.avro.reflect.ReflectData;

/**
 * Avro schema is generated from Java class. Since {@link UserContent} and {@link UserContentWithAck}
 * contain an arbitrary body, we need to construct schema for them explicitly with proper union
 * type set.
 *
 * Alternatively we could expect a client to define schema in Avro text file, and pass it as a
 * parameter when constructing {@link AvroMessageBroker}. This would take however more effort, and
 * is more error prone.
 *
 * @author Tomasz Bak
 */
class MessageBrokerSchema {

    private static final Schema ACKNOWLEDGEMENT_SCHEMA = ReflectData.get().getSchema(Acknowledgement.class);

    static Schema brokerSchemaFrom(List<Class<?>> userTypes) {
        Schema userSchemas = unionOf(userTypes);
        return Schema.createUnion(Arrays.asList(
                ACKNOWLEDGEMENT_SCHEMA, userContentSchema(userSchemas), userContentWithAckSchema(userSchemas)
        ));
    }

    private static Schema userContentSchema(Schema userSchemas) {
        List<Field> fields = new ArrayList<Field>();
        fields.add(new Field("content", userSchemas, null, null));

        Schema schema = Schema.createRecord(UserContent.class.getSimpleName(), null, UserContent.class.getPackage().getName(), false);
        schema.setFields(fields);

        return schema;
    }

    private static Schema userContentWithAckSchema(Schema userSchemas) {
        List<Field> fields = new ArrayList<Field>();
        fields.add(new Field("content", userSchemas, null, null));
        fields.add(new Field("correlationId", Schema.create(Type.STRING), null, null));
        fields.add(new Field("timeout", Schema.create(Type.LONG), null, null));

        Schema schema = Schema.createRecord(UserContentWithAck.class.getSimpleName(), null, UserContentWithAck.class.getPackage().getName(), false);
        schema.setFields(fields);

        return schema;
    }

    private static Schema unionOf(List<Class<?>> types) {
        List<Schema> schemas = new ArrayList<Schema>();
        for (Class<?> c : types) {
            schemas.add(ReflectData.get().getSchema(c));
        }
        return Schema.createUnion(schemas);
    }
}
