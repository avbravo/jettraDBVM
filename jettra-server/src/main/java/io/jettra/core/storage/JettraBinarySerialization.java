package io.jettra.core.storage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom optimized binary serialization format for JettraDB.
 * Efficient packaging of Map<String, Object> structures.
 */
public class JettraBinarySerialization {

    private static final byte TYPE_NULL = 0;
    private static final byte TYPE_BOOLEAN_TRUE = 1;
    private static final byte TYPE_BOOLEAN_FALSE = 2;
    private static final byte TYPE_INTEGER = 3;
    private static final byte TYPE_LONG = 4;
    private static final byte TYPE_DOUBLE = 5;
    private static final byte TYPE_STRING = 6;
    private static final byte TYPE_LIST = 7;
    private static final byte TYPE_MAP = 8;
    // Add more types as needed (ByteArray, Date etc)

    public static void serialize(Map<String, Object> document, DataOutputStream out) throws IOException {
        writeObject(document, out);
    }

    public static Map<String, Object> deserialize(DataInputStream in) throws IOException {
        Object result = readObject(in);
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            return map;
        }
        throw new IOException("Data is not a valid JettraDB document (Map)");
    }

    private static void writeObject(Object obj, DataOutputStream out) throws IOException {
        if (obj == null) {
            out.writeByte(TYPE_NULL);
            return;
        }

        if (obj instanceof Boolean) {
            out.writeByte(((Boolean) obj) ? TYPE_BOOLEAN_TRUE : TYPE_BOOLEAN_FALSE);
        } else if (obj instanceof Integer) {
            out.writeByte(TYPE_INTEGER);
            writeVarInt((Integer) obj, out);
        } else if (obj instanceof Long) {
            out.writeByte(TYPE_LONG);
            out.writeLong((Long) obj);
        } else if (obj instanceof Double) {
            out.writeByte(TYPE_DOUBLE);
            out.writeDouble((Double) obj);
        } else if (obj instanceof String) {
            out.writeByte(TYPE_STRING);
            writeString((String) obj, out);
        } else if (obj instanceof List) {
            out.writeByte(TYPE_LIST);
            writeList((List<?>) obj, out);
        } else if (obj instanceof Map) {
            out.writeByte(TYPE_MAP);
            writeMap((Map<?, ?>) obj, out);
        } else {
            // Fallback to String for unknown types? Or error?
            // For now, toString and warn
            out.writeByte(TYPE_STRING);
            writeString(obj.toString(), out);
        }
    }

    private static Object readObject(DataInputStream in) throws IOException {
        byte type = in.readByte();
        switch (type) {
            case TYPE_NULL:
                return null;
            case TYPE_BOOLEAN_TRUE:
                return true;
            case TYPE_BOOLEAN_FALSE:
                return false;
            case TYPE_INTEGER:
                return readVarInt(in);
            case TYPE_LONG:
                return in.readLong();
            case TYPE_DOUBLE:
                return in.readDouble();
            case TYPE_STRING:
                return readString(in);
            case TYPE_LIST:
                return readList(in);
            case TYPE_MAP:
                return readMap(in);
            default:
                throw new IOException("Unknown type byte: " + type);
        }
    }

    private static void writeString(String str, DataOutputStream out) throws IOException {
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeVarInt(bytes.length, out);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = readVarInt(in);
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void writeList(List<?> list, DataOutputStream out) throws IOException {
        writeVarInt(list.size(), out);
        for (Object item : list) {
            writeObject(item, out);
        }
    }

    private static List<Object> readList(DataInputStream in) throws IOException {
        int size = readVarInt(in);
        List<Object> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(readObject(in));
        }
        return list;
    }

    private static void writeMap(Map<?, ?> map, DataOutputStream out) throws IOException {
        writeVarInt(map.size(), out);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            writeString(entry.getKey().toString(), out); // Keys are always strings in our docs
            writeObject(entry.getValue(), out);
        }
    }

    private static Map<String, Object> readMap(DataInputStream in) throws IOException {
        int size = readVarInt(in);
        // Use LinkedHashMap to preserve order if possible (though hashmap doesnt guarantee)
        Map<String, Object> map = new LinkedHashMap<>(size); 
        for (int i = 0; i < size; i++) {
            String key = readString(in);
            Object value = readObject(in);
            map.put(key, value);
        }
        return map;
    }

    // --- VarInt Implementation ---

    /**
     * Writes an integer using variable-length encoding (VarInt).
     * Similar to Protocol Buffers varint.
     * Use 7 bits per byte, MSB indicates if more bytes follow.
     */
    private static void writeVarInt(int value, DataOutputStream out) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value & 0x7F);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        byte b;
        do {
            b = in.readByte();
            value |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) {
                throw new IOException("VarInt too long or malformed");
            }
        } while ((b & 0x80) != 0);
        return value;
    }

}
