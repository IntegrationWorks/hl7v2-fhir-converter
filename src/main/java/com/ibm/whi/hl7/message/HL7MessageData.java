/*
 * (C) Copyright IBM Corp. 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.whi.hl7.message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.ibm.whi.core.data.JexlEngineUtil;
import com.ibm.whi.core.expression.GenericResult;
import com.ibm.whi.core.expression.Specification;
import com.ibm.whi.core.message.InputData;
import com.ibm.whi.hl7.data.Hl7RelatedGeneralUtils;
import com.ibm.whi.hl7.exception.DataExtractionException;
import com.ibm.whi.hl7.expression.HL7Specification;
import com.ibm.whi.hl7.parsing.HL7DataExtractor;
import com.ibm.whi.hl7.parsing.result.ParsingResult;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;

public class HL7MessageData implements InputData {
  private HL7DataExtractor hde;

  private static final Logger LOGGER = LoggerFactory.getLogger(HL7MessageData.class);
  protected static final Pattern HL7_SPEC_SPLITTER = Pattern.compile(".");
  private static final JexlEngineUtil JEXL =
      new JexlEngineUtil("GeneralUtils", Hl7RelatedGeneralUtils.class);

  public HL7MessageData(HL7DataExtractor hde) {
    Preconditions.checkArgument(hde != null, "Hl7DataExtractor cannot be null.");
    this.hde = hde;
  }


  @Override
  public GenericResult extractValueForSpec(List<Specification> hl7specs,
      Map<String, GenericResult> contextValues) {
    
    GenericResult fetchedValue = null;
    for (Specification hl7specValue : hl7specs) {
      if (hl7specValue instanceof HL7Specification) {

      HL7Specification hl7spec = (HL7Specification) hl7specValue;
      fetchedValue = valuesFromHl7Message(hl7spec, ImmutableMap.copyOf(contextValues));
      // break the loop and return
      if (fetchedValue != null && !fetchedValue.isEmpty()) {
          return getReturnValue(fetchedValue, hl7spec);
        }
      }
    }
    return new GenericResult(null);


  }


  @Override
  public GenericResult extractMultipleValuesForSpec(List<Specification> hl7specs,
      Map<String, GenericResult> contextValues) {
    GenericResult fetchedValue = null;
    for (Specification hl7specValue : hl7specs) {
      if (hl7specValue instanceof HL7Specification) {

        HL7Specification hl7spec = (HL7Specification) hl7specValue;
        fetchedValue = valuesFromHl7Message(hl7spec, ImmutableMap.copyOf(contextValues));
        // break the loop and return
        if (fetchedValue != null && !fetchedValue.isEmpty()) {
          return fetchedValue;
        }
      }
    }
    return new GenericResult(null);

  }


  private static GenericResult getReturnValue(GenericResult fetchedValue,
      HL7Specification hl7spec) {
    if (hl7spec.isExtractMultiple()) {
      return fetchedValue;
    } else {
      return new GenericResult(getSingleValue(fetchedValue.getValue()));
    }
  }



  private static Object getSingleValue(Object object) {
    if (object instanceof List) {
      List value = (List) object;
      if (value.isEmpty()) {
        return null;
      } else {
        return value.get(0);
      }

    }
    return object;
  }


  private GenericResult valuesFromHl7Message(HL7Specification hl7spec,
      ImmutableMap<String, GenericResult> contextValues) {

    GenericResult valuefromVariables;
    if (StringUtils.isNotBlank(hl7spec.getSegment())) {
      valuefromVariables = contextValues.get(hl7spec.getSegment());
    } else if (StringUtils.isNotBlank(hl7spec.getField())) {
      valuefromVariables = contextValues.get(hl7spec.getField());
    } else {
      valuefromVariables = null;
    }

    Object obj = null;
    if (valuefromVariables != null) {
      obj = valuefromVariables.getValue();
    }
    GenericResult res = null;
    try {
      if (obj instanceof Segment) {
        res = extractSpecValuesFromSegment(obj, hl7spec);

      } else if (obj instanceof Type) {
        res = extractSpecValuesFromField(obj, hl7spec);
      } else if (obj == null) {
        res = extractSpecValues(hl7spec);

      }
    } catch (DataExtractionException e) {
      LOGGER.error("cannot extract value for variable {} ", hl7spec, e);
    }

    return res;

  }





  private GenericResult extractSpecValues(HL7Specification hl7spec) {
    if (StringUtils.isNotBlank(hl7spec.getSegment())) {
      ParsingResult<?> res;
      if (StringUtils.isNotBlank(hl7spec.getField())) {
        res = hde.get(hl7spec.getSegment(), hl7spec.getField());
      } else {
        res = hde.getAllStructures(hl7spec.getSegment());
      }

      if (res != null) {
      return new GenericResult(res.getValue());
    }
    }
    return null;
  }


  private GenericResult extractSpecValuesFromSegment(Object obj, HL7Specification hl7spec) {
    if (StringUtils.isNotBlank(hl7spec.getField()) && NumberUtils.isCreatable(hl7spec.getField())) {
      int field = NumberUtils.toInt(hl7spec.getField());
      ParsingResult<?> res = hde.getTypes((Segment) obj, field);
      if (res != null && !res.isEmpty()) {
        return new GenericResult(res.getValues());
      } else {
        return null;
      }

    } else {
      return new GenericResult(obj);
    }
  }


  private GenericResult extractSpecValuesFromField(Object obj, HL7Specification hl7spec) {

    if (hl7spec.getComponent() >= 0) {
      ParsingResult<?> res;
      if (hl7spec.getSubComponent() >= 0) {
        res = hde.getComponent((Type) obj, hl7spec.getComponent(), hl7spec.getSubComponent());
      } else {
        res = hde.getComponent((Type) obj, hl7spec.getComponent());
      }
      
      if(res!=null && !res.isEmpty()) {
        return new GenericResult(res.getValues());
      } else {
        return null;
      }
    } else {
      return new GenericResult(obj);
    }



  }


  public HL7DataExtractor getHL7DataParser() {
    return hde;
  }


  @Override
  public GenericResult evaluateJexlExpression(String expression,
      Map<String, GenericResult> contextValues) {
    Preconditions.checkArgument(StringUtils.isNotBlank(expression), "jexlExp cannot be blank");
    Preconditions.checkArgument(contextValues != null, "context cannot be null");
    String trimedJexlExp = StringUtils.trim(expression);
    Map<String, Object> localContext = new HashMap<>();
    Map<String, GenericResult> resolvedVariables = new HashMap<>(contextValues);
    resolvedVariables.forEach((key, value) -> localContext.put(key, value.getValue()));
    Object obj = JEXL.evaluate(trimedJexlExp, localContext);
    if (obj != null) {
      return new GenericResult(obj);
    } else {
      return null;
    }
  }




}