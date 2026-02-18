package org.opentripplanner.framework.graphql.scalar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.IntValue;
import graphql.language.StringValue;
import java.math.BigInteger;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.basic.Cost;

class CostScalarFactoryTest {

  private static final Cost COST_ONE_HOUR = Cost.costOfSeconds(3600);
  private static final GraphQLContext CONTEXT = GraphQLContext.getDefault();
  private static final Locale LOCALE = Locale.ENGLISH;
  private static final CoercedVariables VARIABLES = CoercedVariables.emptyVariables();

  @Test
  void parseValueWithString() {
    var result = CostScalarFactory.costScalar()
      .getCoercing()
      .parseValue("1h0m0s", CONTEXT, LOCALE);
    assertEquals(COST_ONE_HOUR, result);
  }

  @Test
  void parseValueWithInteger() {
    var result = CostScalarFactory.costScalar().getCoercing().parseValue(3600, CONTEXT, LOCALE);
    assertEquals(COST_ONE_HOUR, result);
  }

  @Test
  void parseLiteralWithStringValue() {
    var result = CostScalarFactory.costScalar()
      .getCoercing()
      .parseLiteral(StringValue.of("1h0m0s"), VARIABLES, CONTEXT, LOCALE);
    assertEquals(COST_ONE_HOUR, result);
  }

  @Test
  void parseLiteralWithIntValue() {
    var result = CostScalarFactory.costScalar()
      .getCoercing()
      .parseLiteral(new IntValue(BigInteger.valueOf(3600)), VARIABLES, CONTEXT, LOCALE);
    assertEquals(COST_ONE_HOUR, result);
  }

  @Test
  void serialize() {
    var result = CostScalarFactory.costScalar()
      .getCoercing()
      .serialize(COST_ONE_HOUR, CONTEXT, LOCALE);
    assertEquals("PT1H", result);
  }
}
