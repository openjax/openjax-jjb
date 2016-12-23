/* Copyright (c) 2015 Seva Safris
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

package org.safris.xjb.generator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.safris.commons.lang.Resources;
import org.safris.commons.lang.Strings;
import org.safris.commons.util.Collections;
import org.safris.commons.xml.XMLException;
import org.safris.commons.xml.XMLText;
import org.safris.commons.xml.dom.DOMStyle;
import org.safris.commons.xml.dom.DOMs;
import org.safris.maven.common.Log;
import org.safris.xjb.runtime.Binding;
import org.safris.xjb.runtime.EncodeException;
import org.safris.xjb.runtime.JSArray;
import org.safris.xjb.runtime.JSBundle;
import org.safris.xjb.runtime.JSObject;
import org.safris.xjb.runtime.Property;
import org.safris.xjb.runtime.validator.PatternValidator;
import org.safris.xjb.xjs.xe.$xjb_boolean;
import org.safris.xjb.xjs.xe.$xjb_element;
import org.safris.xjb.xjs.xe.$xjb_named;
import org.safris.xjb.xjs.xe.$xjb_number;
import org.safris.xjb.xjs.xe.$xjb_object;
import org.safris.xjb.xjs.xe.$xjb_property;
import org.safris.xjb.xjs.xe.$xjb_ref;
import org.safris.xjb.xjs.xe.$xjb_string;
import org.safris.xjb.xjs.xe.xjb_json;
import org.safris.xsb.runtime.BindingList;
import org.safris.xsb.runtime.Bindings;
import org.xml.sax.InputSource;

public class Generator {
  public static void main(final String[] args) throws Exception {
    Generator.generate(Resources.getResource(args[0]).getURL(), new File(args[1]));
  }

  public static void generate(final URL url, final File destDir) throws GeneratorExecutionException, IOException, XMLException {
    final xjb_json json = (xjb_json)Bindings.parse(new InputSource(url.openStream()));
    if (json._object() == null) {
      Log.error("Missing <object> elements: " + url.toExternalForm());
      return;
    }

    final String packageName = "xjb";
    final File outDir = new File(destDir, packageName.replace('.', '/'));
    if (!outDir.exists() && !outDir.mkdirs())
      throw new IOException("Unable to mkdirs: " + outDir.getAbsolutePath());

    for (final xjb_json._object object : json._object())
      objectNameToObject.put(object._name$().text(), object);

    final String name = json._name$().text();

    String out = "";

    out += "package " + packageName + ";";
    out += "\n\n@" + SuppressWarnings.class.getName() + "(\"all\")";
    out += "\npublic class " + name + " extends " + JSBundle.class.getName() + " {";
    out += "\n  public static final " + String.class.getName() + " mimeType = \"" + json._mimeType$().text() + "\";";
    out += "\n  private static " + name + " instance = null;";
    out += "\n\n  protected static " + name + " instance() {";
    out += "\n    return instance == null ? instance = new " + name + "() : instance;";
    out += "\n  }";

    out += "\n\n  @" + Override.class.getName();
    out += "\n  protected " + String.class.getName() + " getSpec() {";
    out += "\n    return \"" + DOMs.domToString(json.marshal(), DOMStyle.INDENT).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\";";
    out += "\n  }";

    final Stack<String> parents = new Stack<String>();
    parents.push(name);
    for (final xjb_json._object object : json._object())
      out += writeJavaClass(parents, object, 0);

    out += "\n\n  private " + name + "() {";
    out += "\n  }";
    out += "\n}";
    try (final FileOutputStream fos = new FileOutputStream(new File(outDir, name + ".java"))) {
      fos.write(out.toString().getBytes());
    }
  }

  private static final Map<String,xjb_json._object> objectNameToObject = new HashMap<String,xjb_json._object>();

  private static String getType(final Stack<String> parent, final $xjb_property property) throws GeneratorExecutionException {
    if (property instanceof $xjb_string)
      return String.class.getName();

    if (property instanceof $xjb_number)
      return Number.class.getName();

    if (property instanceof $xjb_boolean)
      return Boolean.class.getName();

    if (property instanceof $xjb_ref) {
      final String objectName = (($xjb_ref)property)._object$().text();
      if (!objectNameToObject.get(objectName)._abstract$().isNull() && objectNameToObject.get(objectName)._abstract$().text())
        throw new GeneratorExecutionException("Cannot ref to an abstract object \"" + objectName + "\"");

      return Strings.toClassCase(objectName);
    }

    if (property instanceof $xjb_object)
      return Collections.toString(parent, ".") + "." + Strings.toClassCase((($xjb_object)property)._name$().text());

    throw new UnsupportedOperationException("Unknown type: " + property.getClass().getName());
  }

  private static String getPropertyName(final $xjb_property property) {
    if (property instanceof $xjb_named)
      return (($xjb_named)property)._name$().text();

    if (property instanceof $xjb_ref)
      return (($xjb_ref)property)._object$().text();

    if (property instanceof $xjb_object)
      return (($xjb_object)property)._name$().text();

    throw new UnsupportedOperationException("Unexpected type: " + property);
  }

  private static String getInstanceName(final $xjb_property property) {
    return Strings.toInstanceCase(getPropertyName(property));
  }

  private static String writeField(final Stack<String> parent, final $xjb_property property, final int depth) throws GeneratorExecutionException {
    final String valueName = getPropertyName(property);
    final boolean isArray = property._array$().text() != null && property._array$().text();
    final String rawType = getType(parent, property);
    final String type = isArray ? Collection.class.getName() + "<" + rawType + ">" : rawType;

    final String instanceName = getInstanceName(property);

    final String pad = Strings.padFixed("", depth * 2, false);
    String out = "";
    out += "\n\n" + pad + "   public final " + Property.class.getName() + "<" + type + "> " + instanceName + " = new " + Property.class.getName() + "<" + type + ">(this, (" + Binding.class.getName() + "<" + type + ">)bindings.get(\"" + valueName + "\"));";
    out += "\n\n" + pad + "   public " + type + " " + instanceName + "() {";
    out += "\n" + pad + "     return get(" + instanceName + ");";
    out += "\n" + pad + "   }";
    out += "\n\n" + pad + "   public final void " + instanceName + "(final " + type + " _value) {";
    out += "\n" + pad + "     set(" + instanceName + ", _value);";
    out += "\n" + pad + "   }";
    if (isArray) {
      out += "\n\n" + pad + "   public final void " + instanceName + "(final " + rawType + " ... value) {";
      out += "\n" + pad + "     set(" + instanceName + ", " + Collections.class.getName() + ".asCollection(" + ArrayList.class.getName() + ".class, value));";
      out += "\n" + pad + "   }";
    }
    return out;
  }

  private static String writeEncode(final $xjb_property property, final int depth) {
    final String valueName = getPropertyName(property);
    final String instanceName = getInstanceName(property);
    final String pad = Strings.padFixed("", depth * 2, false);

    String out = "";
    if (property._required$().text()) {
      out += "\n" + pad + "     if (!wasSet(" + instanceName + "))";
      out += "\n" + pad + "       throw new " + EncodeException.class.getName() + "(\"\\\"" + valueName + "\\\" is required\", this);\n";
    }

    if (!property._null$().text()) {
      out += "\n" + pad + "     if (wasSet(" + instanceName + ") && get(" + instanceName + ") == null)";
      out += "\n" + pad + "       throw new " + EncodeException.class.getName() + "(\"\\\"" + valueName + "\\\" cannot be null\", this);\n";
    }

    out += "\n" + pad + "     if (wasSet(" + instanceName + "))";
    out += "\n" + pad + "       out.append(\",\\n\").append(pad(depth)).append(\"\\\"" + valueName + "\\\": \").append(";
    if (!property._array$().isNull() && property._array$().text())
      return out + JSArray.class.getName() + ".toString(encode(" + instanceName + "), depth + 1));\n";

    if (property instanceof $xjb_ref)
      return out + "get(" + instanceName + ") != null ? encode(encode(" + instanceName + "), depth + 1) : \"null\");\n";

    if (property instanceof $xjb_string)
      return out + "get(" + instanceName + ") != null ? \"\\\"\" + encode(" + instanceName + ") + \"\\\"\" : \"null\");\n";

    if (property instanceof $xjb_object)
      return out + "encode(encode(" + instanceName + "), depth + 1));\n";

    return out + "encode(" + instanceName + "));\n";
  }

  private static String writeJavaClass(final Stack<String> parent, final $xjb_element object, final int depth) throws GeneratorExecutionException {
    final $xjb_object object1;
    final xjb_json._object object2;
    if (object instanceof $xjb_object) {
      object1 = ($xjb_object)object;
      object2 = null;
    }
    else {
      object1 = null;
      object2 = (xjb_json._object)object;
    }

    final String objectName = (object1 != null ? object1._name$() : object2._name$()).text();
    String out = "";

    final boolean isAbstract = object instanceof xjb_json._object ? ((xjb_json._object)object)._abstract$().text() : false;
    final String extendsPropertyName;
    if (object1 != null)
      extendsPropertyName = !object1._extends$().isNull() ? object1._extends$().text() : null;
    else
      extendsPropertyName = !object2._extends$().isNull() ? object2._extends$().text() : null;

    final String className = Strings.toClassCase(objectName);
    parent.add(className);

    final BindingList<$xjb_property> properties = object1 != null ? object1._property() : object2._property();

    final String pad = Strings.padFixed("", depth * 2, false);
    out += "\n\n" + pad + " public static" + (isAbstract ? " abstract" : "") + " class " + className + " extends " + (extendsPropertyName != null ? parent.get(0) + "." + Strings.toClassCase(extendsPropertyName) : JSObject.class.getName()) + " {";
    out += "\n" + pad + "   private static final " + String.class.getName() + " _name = \"" + objectName + "\";\n";
    out += "\n" + pad + "   private static final " + Map.class.getName() + "<" + String.class.getName() + "," + Binding.class.getName() + "<?>> bindings = new " + HashMap.class.getName() + "<" + String.class.getName() + "," + Binding.class.getName() + "<?>>(" + (properties != null ? properties.size() : 0) + ");";

    out += "\n" + pad + "   static {";
    out += "\n" + pad + "     registerBinding(_name, " + className + ".class);";
    if (properties != null) {
      out += "\n" + pad + "     try {";
      for (final $xjb_property property : properties) {
        final String valueName = getPropertyName(property);
        final String rawType = getType(parent, property);
        final boolean isArray = property._array$().text() != null && property._array$().text();
        final String type = isArray ? Collection.class.getName() + "<" + rawType + ">" : rawType;

        out += "\n" + pad + "       bindings.put(\"" + valueName + "\", new " + Binding.class.getName() + "<" + type + ">(\"" + valueName + "\", " + className + ".class.getDeclaredField(\"" + getInstanceName(property) + "\"), " + rawType + ".class, " + isAbstract + ", " + isArray + ", " + property._required$().text() + ", " + !property._null$().text();
        if (property instanceof $xjb_string) {
          final $xjb_string string = ($xjb_string)property;
          if (string._pattern$().text() != null)
            out += ", new " + PatternValidator.class.getName() + "(\"" + XMLText.unescapeXMLText(string._pattern$().text()).replace("\\", "\\\\") + "\")";
        }

        out += "));";
      }

      out += "\n" + pad + "     }";
      out += "\n" + pad + "     catch (final " + ReflectiveOperationException.class.getName() + " e) {";
      out += "\n" + pad + "       throw new " + ExceptionInInitializerError.class.getName() + "(e);";
      out += "\n" + pad + "     }";
    }
    out += "\n" + pad + "   }";

    if (properties != null)
      for (final $xjb_property property : properties)
        if (property instanceof $xjb_object)
          out += writeJavaClass(parent, property, depth + 1);

    out += "\n\n" + pad + "   public " + className + "(final " + JSObject.class.getName() + " object) {";
    out += "\n" + pad + "     super(object);";
    if (properties != null) {
      out += "\n" + pad + "     if (!(object instanceof " + className + "))";
      out += "\n" + pad + "       return;";
      out += "\n\n" + pad + "     final " + className + " that = (" + className + ")object;";
      for (final $xjb_property property : properties) {
        final String instanceName = getInstanceName(property);
        out += "\n" + pad + "     clone(this." + instanceName + ", that." + instanceName + ");";
      }
    }

    out += "\n" + pad + "   }";

    out += "\n\n" + pad + "   public " + className + "() {";
    out += "\n" + pad + "     super();";
    out += "\n" + pad + "   }";

    out += "\n\n" + pad + "   @" + Override.class.getName();
    out += "\n" + pad + "   protected " + Binding.class.getName() + "<?> _getBinding(final " + String.class.getName() + " name) {";
    if (extendsPropertyName != null) {
      out += "\n" + pad + "     final " + Binding.class.getName() + " binding = super._getBinding(name);";
      out += "\n" + pad + "     if (binding != null)";
      out += "\n" + pad + "       return binding;\n";
    }
    out += "\n" + pad + "     return bindings.get(name);";
    out += "\n" + pad + "   }\n";
    out += "\n" + pad + "   @" + Override.class.getName();
    out += "\n" + pad + "   protected " + Collection.class.getName() + "<" + Binding.class.getName() + "<?>> _bindings() {";
    if (extendsPropertyName != null) {
      out += "\n" + pad + "     final " + Collection.class.getName() + " bindings = new " + ArrayList.class.getName() + "<" + Binding.class.getName() + "<?>>();";
      out += "\n" + pad + "     bindings.addAll(super._bindings());";
      out += "\n" + pad + "     bindings.addAll(bindings);";
      out += "\n" + pad + "     return bindings;";
    }
    else {
      out += "\n" + pad + "     return bindings.values();";
    }
    out += "\n" + pad + "   }";
    out += "\n\n" + pad + "   @" + Override.class.getName();
    out += "\n" + pad + "   protected " + JSBundle.class.getName() + " _bundle() {";
    out += "\n" + pad + "     return " + parent.get(0) + ".instance();";
    out += "\n" + pad + "   }";
    out += "\n\n" + pad + "   @" + Override.class.getName();
    out += "\n" + pad + "   protected " + String.class.getName() + " _name() {";
    out += "\n" + pad + "     return _name;";
    out += "\n" + pad + "   }";
    if (properties != null) {
      for (final $xjb_property property : properties)
        out += writeField(parent, property, depth);

      out += "\n\n" + pad + "   @" + Override.class.getName();
      out += "\n" + pad + "   protected " + String.class.getName() + " _encode(final int depth) {";
      out += "\n" + pad + "     final " + StringBuilder.class.getName() + " out = new " + StringBuilder.class.getName() + "(super._encode(depth));";
      for (int i = 0; i < properties.size(); i++)
        out += writeEncode(properties.get(i), depth);

      out += "\n" + pad + "     return out." + (extendsPropertyName != null ? "toString()" : "substring(2)") + ";\n" + pad + "   }";
    }

    out += "\n\n" + pad + "   @" + Override.class.getName();
    out += "\n" + pad + "   public " + String.class.getName() + " toString() {";
    out += "\n" + pad + "     return encode(this, 1);";
    out += "\n" + pad + "   }";

    out += "\n\n" + pad + "   @" + Override.class.getName();
    out += "\n" + pad + "   public boolean equals(final " + Object.class.getName() + " obj) {";
    out += "\n" + pad + "     if (obj == this)";
    out += "\n" + pad + "       return true;";
    out += "\n\n" + pad + "     if (!(obj instanceof " + className + ")" + (extendsPropertyName != null ? " || !super.equals(obj)" : "") + ")";
    out += "\n" + pad + "       return false;\n";
    if (properties != null) {
      out += "\n" + pad + "     final " + className + " that = (" + className + ")obj;";
      for (final $xjb_property property : properties) {
        final String instanceName = getInstanceName(property);
        out += "\n" + pad + "     if (that." + instanceName + " != null ? !that." + instanceName + ".equals(" + instanceName + ") : " + instanceName + " != null)";
        out += "\n" + pad + "       return false;\n";
      }
    }
    out += "\n" + pad + "     return true;";
    out += "\n" + pad + "   }";

    out += "\n\n" + pad + "   @" + Override.class.getName();
    out += "\n" + pad + "   public int hashCode() {";
    if (properties != null) {
      out += "\n" + pad + "     int hashCode = " + className.hashCode() + (extendsPropertyName != null ? " ^ 31 * super.hashCode()" : "") + ";";
      for (final $xjb_property property : properties) {
        final String instanceName = getInstanceName(property);
        out += "\n" + pad + "     if (" + instanceName + " != null)";
        out += "\n" + pad + "       hashCode ^= 31 * " + instanceName + ".hashCode();\n";
      }
      out += "\n" + pad + "     return hashCode;";
    }
    else {
      out += "\n" + pad + "     return " + className.hashCode() + (extendsPropertyName != null ? " ^ 31 * super.hashCode()" : "") + ";";
    }
    out += "\n" + pad + "   }";

    out += "\n" + pad + " }";

    parent.pop();
    return out.toString();
  }
}