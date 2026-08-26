package com.ibatulanand.productservice.codec;

import java.math.BigDecimal;

import jakarta.inject.Singleton;
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;

@Singleton
public class BigDecimalCodecProvider implements CodecProvider {

    @Override
    @SuppressWarnings("unchecked")
    public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
        if (clazz == BigDecimal.class) {
            return (Codec<T>) BigDecimalCodec.INSTANCE;
        }
        return null;
    }

    private static final class BigDecimalCodec implements Codec<BigDecimal> {
        private static final BigDecimalCodec INSTANCE = new BigDecimalCodec();

        @Override
        public BigDecimal decode(BsonReader reader, DecoderContext decoderContext) {
            return switch (reader.getCurrentBsonType()) {
                case NULL -> {
                    reader.readNull();
                    yield null;
                }
                case STRING -> new BigDecimal(reader.readString());
                case DECIMAL128 -> reader.readDecimal128().bigDecimalValue();
                case DOUBLE -> BigDecimal.valueOf(reader.readDouble());
                case INT32 -> BigDecimal.valueOf(reader.readInt32());
                case INT64 -> BigDecimal.valueOf(reader.readInt64());
                default -> throw new IllegalArgumentException(
                        "Unsupported BSON type for BigDecimal: " + reader.getCurrentBsonType());
            };
        }

        @Override
        public void encode(BsonWriter writer, BigDecimal value, EncoderContext encoderContext) {
            if (value == null) {
                writer.writeNull();
            } else {
                writer.writeString(value.toPlainString());
            }
        }

        @Override
        public Class<BigDecimal> getEncoderClass() {
            return BigDecimal.class;
        }
    }
}
