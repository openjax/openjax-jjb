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

package org.openjax.jjb.runtime;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;

import org.openjax.standard.util.FastCollections;
import org.openjax.jjb.runtime.validator.Validator;

public class Binding<T> {
  public static final Binding<?> ANY = new Binding<>(null, null, null, false, false, Required.FALSE, false);

  private static String errorsToString(final Property<?> property, final String[] errors) {
    if (errors == null || errors.length == 0)
      return null;

    final StringBuilder message = new StringBuilder();
    for (final String error : errors)
      message.append("\n").append(property.getPath()).append(' ').append(error);

    return message.substring(1);
  }

  public final String name;
  public final Field property;
  public final Class<?> type;
  public final boolean array;
  public final Required required;
  public final boolean notNull;
  public final boolean urlDecode;
  public final boolean urlEncode;
  public final Validator<?>[] validators;

  // string
  @SafeVarargs
  public Binding(final String name, final Field property, final Class<?> type, final boolean isAbstract, final boolean array, final Required required, final boolean notNull, final boolean urlDecode, final boolean urlEncode, final Validator<?> ... validators) {
    this.name = name;
    this.property = property;
    if (this.property != null)
      this.property.setAccessible(true);

    this.type = type;
    this.array = array;
    this.required = required;
    this.notNull = notNull;
    this.urlDecode = urlDecode;
    this.urlEncode = urlEncode;
    this.validators = validators;
  }

  // [other]
  @SafeVarargs
  public Binding(final String name, final Field property, final Class<?> type, final boolean isAbstract, final boolean array, final Required required, final boolean notNull, final Validator<?> ... validators) {
    this(name, property, type, isAbstract, array, required, notNull, false, false, validators);
  }

  protected boolean isAssignable(final T value) {
    final Class<?> type;
    return value == null || (array ? value instanceof List && ((type = FastCollections.getComponentType((List<?>)value)) == null || type.isAssignableFrom(type)) : this.type.isAssignableFrom(type = value.getClass()));
  }

  @SuppressWarnings("unchecked")
  protected String validate(final Property<?> property, final Object value) {
    return errorsToString(property, value instanceof Collection ? Validator.validate((Validator<T>[])validators, (Collection<T>)value) : Validator.validate((Validator<T>[])validators, (T)value));
  }
}