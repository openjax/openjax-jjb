/* Copyright (c) 2015 OpenJAX
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * You should have received a copy of The MIT License (MIT) along with this
 * program. If not, see <http://opensource.org/licenses/MIT/>.
 */

package org.openjax.jjb.runtime.decoder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.openjax.jjb.runtime.Binding;
import org.openjax.jjb.runtime.DecodeException;
import org.openjax.jjb.runtime.JSObjectBase;
import org.openjax.jjb.runtime.JsonReader;

public class NumberDecoder extends Decoder<Number> {
  @Override
  protected Number[] newInstance(final int depth) {
    return new Number[depth];
  }

  @Override
  public Number decode(final JsonReader reader, char ch, final Binding<?> binding) throws DecodeException, IOException {
    if (('0' > ch || ch > '9') && ch != '-') {
      if (JSObjectBase.isNull(ch, reader))
        return null;

      throw new DecodeException("Illegal char for " + getClass().getSimpleName() + ": " + ch, reader);
    }

    final StringBuilder value = new StringBuilder();
    char lastChar = '\0';
    boolean isDecimal = false;
    do {
      isDecimal |= ch == '.' || ((lastChar == 'e' || lastChar == 'E') && ch == '-');
      lastChar = ch;

      value.append(ch);
      reader.mark(1);
    }
    while ('0' <= (ch = JSObjectBase.nextAny(reader)) && ch <= '9' || ch == '.' || ch == 'e' || ch == 'E' || ch == '-');
    reader.reset();

    final String number = value.toString();
    if (binding.type == BigDecimal.class)
      return new BigDecimal(number);

    if (binding.type != null && binding.type != BigInteger.class)
      throw new UnsupportedOperationException("Unsupported number type: " + binding.type.getName());

    if (isDecimal) {
      if (binding.type == null)
        return new BigDecimal(number);

      throw new DecodeException("is not an \"integer\" number: \"" + value + "\"", reader);
    }

    return new BigInteger(number);
  }
}