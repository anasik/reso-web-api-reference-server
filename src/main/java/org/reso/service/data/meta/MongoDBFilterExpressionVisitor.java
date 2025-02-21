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
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
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
        LOG.info("Binary Operator - Kind: {}, Left: {}, Right: {}", operator, left, right);

        if (operator == BinaryOperatorKind.AND) {
            Document leftDoc = Document.parse(left);
            Document rightDoc = Document.parse(right);
            LOG.info("AND operation - Left Doc: {}, Right Doc: {}", leftDoc.toJson(), rightDoc.toJson());
            return new Document("$and", Arrays.asList(Document.parse(left), Document.parse(right))).toJson();
        }

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
            case LT:
                mongoOperator = "$lt";
                break;
            case LE:
                mongoOperator = "$lte";
                break;
            case GE:
                mongoOperator = "$gte";
                break;
            case HAS:
                mongoOperator = "$all";
                break;
            case AND:
                return new Document("$and", Arrays.asList(Document.parse(left), Document.parse(right))).toJson();
            case OR:
                return new Document("$or", Arrays.asList(Document.parse(left), Document.parse(right))).toJson();
            default:
                throw new ODataApplicationException(
                        "Unsupported operator: " + operator,
                        HttpStatusCode.BAD_REQUEST.getStatusCode(),
                        Locale.ENGLISH);
        }

        left = left.replace("property.", "").trim();

        boolean isIntegerField = false;
        boolean isDecimalField = false;
        boolean isDateTimeOffsetField = false;
        boolean isCollectionField = false;
        boolean isEnumField = false;

        FieldInfo targetField = null;
        if (resourceInfo.getFieldList() != null) {
            for (FieldInfo field : resourceInfo.getFieldList()) {
                if (field.getFieldName().equals(left)) {
                    targetField = field;
                    if (field.getType().equals(EdmPrimitiveTypeKind.Int32.getFullQualifiedName()) ||
                            field.getType().equals(EdmPrimitiveTypeKind.Int64.getFullQualifiedName())) {
                        isIntegerField = true;
                    } else if (field.getType().equals(EdmPrimitiveTypeKind.Decimal.getFullQualifiedName()) ||
                            field.getType().equals(EdmPrimitiveTypeKind.Double.getFullQualifiedName()) ||
                            field.getType().equals(EdmPrimitiveTypeKind.Single.getFullQualifiedName())) {
                        isDecimalField = true;
                    } else if (field.getType().equals(EdmPrimitiveTypeKind.DateTimeOffset.getFullQualifiedName())) {
                        isDateTimeOffsetField = true;
                    } else if (field.isCollection()) {
                        isCollectionField = true;
                    }
                    if (field instanceof EnumFieldInfo) {
                        isEnumField = true;
                    }
                    break;
                }
            }
        }

        Object rightValue = right;
        if (isEnumField) {
            rightValue = right.substring(right.indexOf("'") + 1, right.lastIndexOf("'"));
        }
        try {
            if (isIntegerField) {
                rightValue = Long.parseLong(right.trim());
            } else if (isDecimalField) {
                rightValue = Double.parseDouble(right.trim());
            } else if (isDateTimeOffsetField) {
                right = right.trim().replace("'", "").replace("\"", "");
                if (right.matches("^\\d+$")) { // timestamp millis
                    rightValue = new Date(Long.parseLong(right));
                } else {
                    rightValue = Date.from(Instant.parse(right));
                }
            } else {
                rightValue = right.replace("'", "").replace("\"", "");
            }
        } catch (Exception e) {
            throw new ODataApplicationException(
                    "Invalid value for field '" + left + "': " + right,
                    HttpStatusCode.BAD_REQUEST.getStatusCode(),
                    Locale.ENGLISH,
                    e);
        }

        Document comparison;
        if (operator == BinaryOperatorKind.HAS) {
            if (isCollectionField) {
                if (isEnumField) {
                    // For enum collections, we use $in to check if the value exists in the array
                    comparison = new Document(left, new Document("$in", Arrays.asList(rightValue)));
                    LOG.info("Has Filter for enum: {}", comparison.toJson());
                } else {
                    // For non-enum collections, use $elemMatch
                    comparison = new Document(left, new Document("$elemMatch", new Document("$eq", rightValue)));
                    LOG.info("Has Filter for non-enum: {}", comparison.toJson());
                }
            } else {
                throw new ODataApplicationException(
                        "Operator 'has' can only be used with collection fields.",
                        HttpStatusCode.BAD_REQUEST.getStatusCode(),
                        Locale.ENGLISH);
            }
        } else {
            comparison = new Document(left, new Document(mongoOperator, rightValue));
        }

        LOG.info("MongoDB Filter Expression: {}", comparison.toJson());

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
                return new Document("$nor", Arrays.asList(Document.parse(operand))).toJson();
            case MINUS:
                return "-" + operand;
            default:
                throw new ODataApplicationException("Unsupported unary operator: " + operator.name(),
                        HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
        }
    }

    @Override
    public String visitMethodCall(MethodKind methodCall, List<String> parameters)
            throws ExpressionVisitException, ODataApplicationException {

        if (methodCall == MethodKind.NOW) {
            return String.valueOf(System.currentTimeMillis());
        }

        throw new ODataApplicationException("Unsupported method call: " + methodCall.name(),
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
        LOG.info("Lambda Expression - Function: {}, Variable: {}, Expression: {}",
                lambdaFunction, lambdaVariable, expression);

        if ("all".equals(lambdaFunction)) {
            LOG.info("Processing 'all' lambda function");
            try {
                String result = expression.accept(this);
                LOG.info("Lambda expression result: {}", result);
                return result;
            } catch (Exception e) {
                LOG.error("Error processing lambda expression", e);
                throw new ODataApplicationException("Error processing lambda expression: " + e.getMessage(),
                        HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
            }
        }

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
