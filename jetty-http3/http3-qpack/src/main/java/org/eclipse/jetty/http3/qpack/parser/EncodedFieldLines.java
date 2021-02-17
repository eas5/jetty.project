package org.eclipse.jetty.http3.qpack.parser;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.http3.qpack.QpackException;

public class EncodedFieldLines
{
    private final NBitIntegerParser _integerParser = new NBitIntegerParser();
    private final NBitStringParser _stringParser = new NBitStringParser();
    private final List<EncodedField> _encodedFields = new ArrayList<>();

    private final int _requiredInsertCount;
    private final boolean _signBit;
    private final int _deltaBase;

    public EncodedFieldLines(int requiredInsertCount, boolean signBit, int deltaBase)
    {
        _requiredInsertCount = requiredInsertCount;
        _signBit = signBit;
        _deltaBase = deltaBase;
    }

    public void parse(ByteBuffer buffer) throws QpackException
    {
        while (buffer.hasRemaining())
        {
            EncodedField encodedField;
            byte firstByte = buffer.get(buffer.position());
            if ((firstByte & 0x80) != 0)
                encodedField = parseIndexedFieldLine(buffer);
            else if ((firstByte & 0x40) != 0)
                encodedField = parseLiteralFieldLineWithNameReference(buffer);
            else if ((firstByte & 0x20) != 0)
                encodedField = parseLiteralFieldLineWithLiteralName(buffer);
            else if ((firstByte & 0x10) != 0)
                encodedField = parseIndexFieldLineWithPostBaseIndex(buffer);
            else
                encodedField = parseLiteralFieldLineWithPostBaseNameReference(buffer);

            _encodedFields.add(encodedField);
        }
    }

    private EncodedField parseIndexedFieldLine(ByteBuffer buffer) throws QpackException
    {
        byte firstByte = buffer.get(buffer.position());
        boolean dynamicTable = (firstByte & 0x40) == 0;
        _integerParser.setPrefix(6);
        int index = _integerParser.decode(buffer);
        if (index < 0)
            throw new QpackException.CompressionException("Invalid Index");
        return new EncodedField.RelativeIndexedField(dynamicTable, index);
    }

    private EncodedField parseLiteralFieldLineWithNameReference(ByteBuffer buffer) throws QpackException
    {
        byte firstByte = buffer.get(buffer.position());
        boolean allowEncoding = (firstByte & 0x20) != 0;
        boolean dynamicTable = (firstByte & 0x10) == 0;

        _integerParser.setPrefix(4);
        int nameIndex = _integerParser.decode(buffer);
        if (nameIndex < 0)
            throw new QpackException.CompressionException("Invalid Name Index");

        _stringParser.setPrefix(8);
        String value = _stringParser.decode(buffer);
        if (value == null)
            throw new QpackException.CompressionException("Value");

        return new EncodedField.RelativeNameReference(allowEncoding, dynamicTable, nameIndex, value);
    }

    private EncodedField parseLiteralFieldLineWithLiteralName(ByteBuffer buffer) throws QpackException
    {
        byte firstByte = buffer.get(buffer.position());
        boolean allowEncoding = (firstByte & 0x10) != 0;

        _stringParser.setPrefix(3);
        String name = _stringParser.decode(buffer);
        if (name == null)
            throw new QpackException.CompressionException("Invalid Name");

        _stringParser.setPrefix(8);
        String value = _stringParser.decode(buffer);
        if (value == null)
            throw new QpackException.CompressionException("Invalid Value");

        return new EncodedField.LiteralField(allowEncoding, name, value);
    }

    private EncodedField parseLiteralFieldLineWithPostBaseNameReference(ByteBuffer buffer) throws QpackException
    {
        byte firstByte = buffer.get(buffer.position());
        boolean allowEncoding = (firstByte & 0x08) != 0;

        _integerParser.setPrefix(3);
        int nameIndex = _integerParser.decode(buffer);
        if (nameIndex < 0)
            throw new QpackException.CompressionException("Invalid Index");

        _stringParser.setPrefix(8);
        String value = _stringParser.decode(buffer);
        if (value == null)
            throw new QpackException.CompressionException("Invalid Value");

        return new EncodedField.AbsoluteNameReference(allowEncoding, nameIndex, value);
    }

    private EncodedField parseIndexFieldLineWithPostBaseIndex(ByteBuffer buffer) throws QpackException
    {
        _integerParser.setPrefix(4);
        int index = _integerParser.decode(buffer);
        if (index < 0)
            throw new QpackException.CompressionException("Invalid Index");

        return new EncodedField.AbsoluteIndexedField(index);
    }

    public static class EncodedField
    {
        public static class LiteralField extends EncodedField
        {
            private final boolean _allowEncoding;
            private final String _name;
            private final String _value;

            public LiteralField(boolean allowEncoding, String name, String value)
            {
                _allowEncoding = allowEncoding;
                _name = name;
                _value = value;
            }
        }

        public static class RelativeIndexedField extends EncodedField
        {
            private final boolean _dynamicTable;
            private final int _index;

            public RelativeIndexedField(boolean dynamicTable, int index)
            {
                _dynamicTable = dynamicTable;
                _index = index;
            }
        }

        public static class AbsoluteIndexedField extends EncodedField
        {
            private final int _index;

            public AbsoluteIndexedField(int index)
            {
                _index = index;
            }
        }

        public static class RelativeNameReference extends EncodedField
        {
            private final boolean _allowEncoding;
            private final boolean _dynamicTable;
            private final int _nameIndex;
            private final String _value;

            public RelativeNameReference(boolean allowEncoding, boolean dynamicTable, int nameIndex, String value)
            {
                _allowEncoding = allowEncoding;
                _dynamicTable = dynamicTable;
                _nameIndex = nameIndex;
                _value = value;
            }
        }

        public static class AbsoluteNameReference extends EncodedField
        {
            private final boolean _allowEncoding;
            private final int _nameIndex;
            private final String _value;

            public AbsoluteNameReference(boolean allowEncoding, int nameIndex, String value)
            {
                _allowEncoding = allowEncoding;
                _nameIndex = nameIndex;
                _value = value;
            }
        }
    }


    public static int decodeInsertCount(int encInsertCount, int totalNumInserts, int maxTableCapacity) throws QpackException
    {
        if (encInsertCount == 0)
            return 0;

        int maxEntries = maxTableCapacity / 32;
        int fullRange = 2 * maxEntries;
        if (encInsertCount > fullRange)
            throw new QpackException.CompressionException("encInsertCount > fullRange");

        // MaxWrapped is the largest possible value of ReqInsertCount that is 0 mod 2 * MaxEntries.
        int maxValue = totalNumInserts + maxEntries;
        int maxWrapped = (maxValue / fullRange) * fullRange;
        int reqInsertCount =  maxWrapped + encInsertCount -1;

        // If reqInsertCount exceeds maxValue, the Encoder's value must have wrapped one fewer time.
        if (reqInsertCount > maxValue)
        {
            if (reqInsertCount <= fullRange)
                throw new QpackException.CompressionException("reqInsertCount <= fullRange");
            reqInsertCount -= fullRange;
        }

        // Value of 0 must be encoded as 0.
        if (reqInsertCount == 0)
            throw new QpackException.CompressionException("reqInsertCount == 0");

        return reqInsertCount;
    }

    public static int encodeInsertCount(int reqInsertCount, int maxTableCapacity)
    {
        if (reqInsertCount == 0)
            return 0;

        int maxEntries = maxTableCapacity / 32;
        return (reqInsertCount % (2 * maxEntries)) + 1;
    }
}
