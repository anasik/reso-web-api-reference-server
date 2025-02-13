package org.reso.service.data.meta;

import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.edm.EdmEnumType;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourcePrimitiveProperty;
import org.apache.olingo.server.api.uri.queryoption.expression.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.reso.service.data.GenericEntityCollectionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.model.Filters;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MongoDBFilterExpressionVisitor implements ExpressionVisitor<String> {
    private static final Logger LOG = LoggerFactory.getLogger(GenericEntityCollectionProcessor.class);
    private final String entityAlias;
    private final ResourceInfo resourceInfo;

    public MongoDBFilterExpressionVisitor(ResourceInfo resourceInfo) {
        this.entityAlias = resourceInfo.getTableName();
        this.resourceInfo = resourceInfo;
    }

    @Override
    public String visitBinaryOperator(BinaryOperatorKind operator, String left, String right)
            throws ExpressionVisitException, ODataApplicationException {
        String mongoOperator;
        switch (operator) {
            case EQ:
                mongoOperator = "$eq";
                break;
            case NE:
                mongoOperator = "$ne";
                break;
            case GT:
                mongoOperator = "$gt";
                break;
            case GE:
                mongoOperator = "$gte";
                break;
            case LT:
                mongoOperator = "$lt";
                break;
            case LE:
                mongoOperator = "$lte";
                break;
            default:
                throw new ODataApplicationException("Unsupported operator: " + operator,
                        HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
        }

        left = left.replace("property.", "");

        boolean isIntegerField = false;
        if (resourceInfo.getFieldList() != null) {
            for (FieldInfo field : resourceInfo.getFieldList()) {
                if (field.getFieldName().equals(left)
                        && (field.getType().equals(EdmPrimitiveTypeKind.Int32.getFullQualifiedName())
                                || field.getType().equals(EdmPrimitiveTypeKind.Int64.getFullQualifiedName()))) {
                    isIntegerField = true;
                    break;
                }
            }
        }

        Document comparison;
        if (isIntegerField) {
            comparison = new Document("$expr", new Document(mongoOperator, Arrays.asList(
                    new Document("$toInt", "$" + left),
                    Integer.parseInt(right))));
        } else {
            comparison = new Document(left, new Document(mongoOperator, right));
        }

        return comparison.toJson();
    }

    @Override
    public String visitBinaryOperator(BinaryOperatorKind operator, String s, List<String> list)
            throws ExpressionVisitException, ODataApplicationException {
        // String strOperator = BINARY_OPERATORS.get(operator);
        throw new ODataApplicationException("Unsupported binary operation: " + operator.name(),
                operator == BinaryOperatorKind.HAS ? HttpStatusCode.NOT_IMPLEMENTED.getStatusCode()
                        : HttpStatusCode.BAD_REQUEST.getStatusCode(),
                Locale.ENGLISH);
    }

    @Override
    public String visitUnaryOperator(UnaryOperatorKind operator, String operand)
            throws ExpressionVisitException, ODataApplicationException {
        switch (operator) {
            case NOT:
                return "NOT " + operand;
            case MINUS:
                return "-" + operand;
        }
        throw new ODataApplicationException("Wrong unary operator: " + operator,
                HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public String visitMethodCall(MethodKind methodCall, List<String> parameters)
            throws ExpressionVisitException, ODataApplicationException {
        if (parameters.isEmpty() && methodCall.equals(MethodKind.NOW)) {
            return "CURRENT_DATE";
        }
        String firsEntityParam = parameters.get(0);
        switch (methodCall) {
            case CONTAINS:
                return firsEntityParam + " LIKE '%" + extractFromStringValue(parameters.get(1)) + "%'";
            case STARTSWITH:
                return firsEntityParam + " LIKE '" + extractFromStringValue(parameters.get(1)) + "%'";
            case ENDSWITH:
                return firsEntityParam + " LIKE '%" + extractFromStringValue(parameters.get(1));
            case DAY:
                return "DAY(" + firsEntityParam + ")";
            case MONTH:
                return "MONTH(" + firsEntityParam + ")";
            case YEAR:
                return "YEAR(" + firsEntityParam + ")";
        }
        throw new ODataApplicationException("Method call " + methodCall + " not implemented",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public String visitLiteral(Literal literal) throws ExpressionVisitException, ODataApplicationException {
        String literalAsString = literal.getText();
        if (literal.getType() == null) {
            literalAsString = "NULL";
        }
        if (literal.getType().getFullQualifiedName()
                .equals(EdmPrimitiveTypeKind.DateTimeOffset.getFullQualifiedName())) {
            return "'" + literalAsString + "'";
        }

        return literalAsString;
    }

    @Override
    public String visitMember(Member member) throws ExpressionVisitException, ODataApplicationException {
        List<UriResource> resources = member.getResourcePath().getUriResourceParts();

        UriResource first = resources.get(0);

        // TODO: Enum and ComplexType; joins
        if (resources.size() == 1 && first instanceof UriResourcePrimitiveProperty) {
            UriResourcePrimitiveProperty primitiveProperty = (UriResourcePrimitiveProperty) first;
            return entityAlias + "." + primitiveProperty.getProperty().getName();
        } else {
            throw new ODataApplicationException("Only primitive properties are implemented in filter expressions",
                    HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
        }
    }

    @Override
    public String visitEnum(EdmEnumType type, List<String> enumValues)
            throws ExpressionVisitException, ODataApplicationException {
        throw new ODataApplicationException("Enums are not implemented", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(),
                Locale.ENGLISH);
    }

    @Override
    public String visitLambdaExpression(String lambdaFunction, String lambdaVariable, Expression expression)
            throws ExpressionVisitException, ODataApplicationException {
        throw new ODataApplicationException("Lambda expressions are not implemented",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public String visitAlias(String aliasName) throws ExpressionVisitException, ODataApplicationException {
        throw new ODataApplicationException("Aliases are not implemented",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public String visitTypeLiteral(EdmType type) throws ExpressionVisitException, ODataApplicationException {
        throw new ODataApplicationException("Type literals are not implemented",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public String visitLambdaReference(String variableName) throws ExpressionVisitException, ODataApplicationException {
        throw new ODataApplicationException("Lambda references are not implemented",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    private String extractFromStringValue(String val) {
        return val.substring(1, val.length() - 1);
    }

}
