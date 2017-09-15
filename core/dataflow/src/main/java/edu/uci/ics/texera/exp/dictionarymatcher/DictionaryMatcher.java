package edu.uci.ics.texera.exp.dictionarymatcher;

import edu.uci.ics.texera.api.constants.ErrorMessages;
import edu.uci.ics.texera.api.constants.SchemaConstants;
import edu.uci.ics.texera.api.dataflow.IOperator;
import edu.uci.ics.texera.api.exception.DataflowException;
import edu.uci.ics.texera.api.exception.TexeraException;
import edu.uci.ics.texera.api.field.ListField;
import edu.uci.ics.texera.api.schema.Attribute;
import edu.uci.ics.texera.api.schema.AttributeType;
import edu.uci.ics.texera.api.schema.Schema;
import edu.uci.ics.texera.api.span.Span;
import edu.uci.ics.texera.api.tuple.Tuple;
import edu.uci.ics.texera.api.utils.Utils;
import edu.uci.ics.texera.exp.common.AbstractSingleInputOperator;
import edu.uci.ics.texera.exp.keywordmatcher.KeywordMatchingType;
import edu.uci.ics.texera.exp.utils.DataflowUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * Created by Chang on 6/28/17.
 */
public class DictionaryMatcher extends AbstractSingleInputOperator {

    private final DictionaryPredicate predicate;

    public DictionaryMatcher(DictionaryPredicate predicate) {
        this.predicate = predicate;
    }

    private Schema inputSchema;

    @Override
    protected void setUp() throws TexeraException {

        if (inputOperator == null) {
            throw new DataflowException(ErrorMessages.INPUT_OPERATOR_NOT_SPECIFIED);
        }
        inputSchema = inputOperator.getOutputSchema();
        predicate.getDictionary().resetCursor();

        if (predicate.getDictionary().isEmpty()) {
            throw new DataflowException("Dictionary is empty");
        }

        inputSchema = inputOperator.getOutputSchema();
        outputSchema = inputSchema;

        if (!inputSchema.containsField(SchemaConstants.PAYLOAD)) {
            outputSchema = Utils.addAttributeToSchema(outputSchema, SchemaConstants.PAYLOAD_ATTRIBUTE);
        }
        if (inputSchema.containsField(predicate.getSpanListName())) {
            throw new DataflowException(ErrorMessages.DUPLICATE_ATTRIBUTE(predicate.getSpanListName(), inputSchema));
        } else {
            outputSchema = Utils.addAttributeToSchema(outputSchema,
                    new Attribute(predicate.getSpanListName(), AttributeType.LIST));
        }
        if (predicate.getKeywordMatchingType() == KeywordMatchingType.CONJUNCTION_INDEXBASED) {
            predicate.getDictionary().setDictionaryTokenSetList(predicate.getAnalyzerString());
        } else if (predicate.getKeywordMatchingType() == KeywordMatchingType.PHRASE_INDEXBASED) {
            predicate.getDictionary().setDictionaryTokenListWithStopwords(predicate.getAnalyzerString());
            predicate.getDictionary().setDictionaryTokenSetList(predicate.getAnalyzerString());
        } else if (predicate.getKeywordMatchingType() == KeywordMatchingType.REGEX) {
            predicate.getDictionary().setPatternList();
        }
    }

    @Override
    protected Tuple computeNextMatchingTuple() throws TexeraException {
        Tuple inputTuple;
        Tuple resultTuple = null;
        while ((inputTuple = inputOperator.getNextTuple()) != null) {
            resultTuple = processOneInputTuple(inputTuple);
            if (resultTuple != null) {
                break;
            }
        }

        return resultTuple;

    }

    @Override
    public Tuple processOneInputTuple(Tuple inputTuple) throws TexeraException {

        if (inputTuple == null) {
            return null;
        }
        if (!inputSchema.containsField(SchemaConstants.PAYLOAD)) {
            inputTuple = DataflowUtils.getSpanTuple(inputTuple.getFields(),
                    DataflowUtils.generatePayloadFromTuple(inputTuple, predicate.getAnalyzerString()), outputSchema);
        }
        if (predicate.getSpanListName() != null) {
            inputTuple = DataflowUtils.getSpanTuple(inputTuple.getFields(), new ArrayList<Span>(), outputSchema);
        }
        List<Span> matchingResults = null;
        if (predicate.getKeywordMatchingType() == KeywordMatchingType.CONJUNCTION_INDEXBASED) {

            ArrayList<String> dictionaryEntries = predicate.getDictionary().getDictionaryEntries();
            ArrayList<Set<String>> tokenSetsNoStopwords = predicate.getDictionary().getTokenSetsNoStopwords();

            matchingResults = appendConjunctionMatchingSpans4Dictionary(inputTuple, predicate.getAttributeNames(), tokenSetsNoStopwords, dictionaryEntries);

        } else if (predicate.getKeywordMatchingType() == KeywordMatchingType.PHRASE_INDEXBASED) {

            ArrayList<String> dictionaryEntries = predicate.getDictionary().getDictionaryEntries();
            ArrayList<List<String>> tokenListsNoStopwords = predicate.getDictionary().getTokenListsNoStopwords();
            ArrayList<List<String>> tokenListsWithStopwords = predicate.getDictionary().getTokenListsWithStopwords();
            ArrayList<Set<String>> tokenSetsNoStopwords = predicate.getDictionary().getTokenSetsNoStopwords();

            matchingResults = appendPhraseMatchingSpans4Dictionary(inputTuple, predicate.getAttributeNames(), tokenListsNoStopwords, tokenSetsNoStopwords, tokenListsWithStopwords, dictionaryEntries);

        } else if (predicate.getKeywordMatchingType() == KeywordMatchingType.SUBSTRING_SCANBASED) {
            predicate.getDictionary().resetCursor();
            String currentDictionaryEntry;
            matchingResults = new ArrayList<>();

            while ((currentDictionaryEntry = predicate.getDictionary().getNextEntry()) != null) {
                DataflowUtils.appendSubstringMatchingSpans(inputTuple, predicate.getAttributeNames(), currentDictionaryEntry, matchingResults);
            }

        } else if (predicate.getKeywordMatchingType() == KeywordMatchingType.REGEX) {

            ArrayList<Pattern> patternList = predicate.getDictionary().getPatternList();
            ArrayList<String> dictionaryEntries = predicate.getDictionary().getDictionaryEntries();
            matchingResults = new ArrayList<>();

            for (int i = 0; i < dictionaryEntries.size(); i++) {
                for (String attributeName : predicate.getAttributeNames()) {
                    AttributeType attributeType = inputTuple.getSchema().getAttribute(attributeName).getAttributeType();
                    String fieldValue = inputTuple.getField(attributeName).getValue().toString();

                    // types other than TEXT and STRING: throw Exception for now
                    if (attributeType != AttributeType.STRING && attributeType != AttributeType.TEXT) {
                        throw new DataflowException("KeywordMatcher: Fields other than STRING and TEXT are not supported yet");
                    }

                    Matcher javaMatcher = patternList.get(i).matcher(fieldValue);
                    while (javaMatcher.find()) {
                        int start = javaMatcher.start();
                        int end = javaMatcher.end();
                        matchingResults.add(
                                new Span(attributeName, start, end, dictionaryEntries.get(i), fieldValue.substring(start, end)));
                    }
                }
            }

        }

        if (matchingResults == null || matchingResults.isEmpty()) {
            return null;
        }

        ListField<Span> spanListField = inputTuple.getField(predicate.getSpanListName());
        List<Span> spanList = spanListField.getValue();
        spanList.addAll(matchingResults);

        return inputTuple;
    }

    private List<Span> appendConjunctionMatchingSpans4Dictionary(Tuple inputTuple, List<String> attributeNames, List<Set<String>> queryTokenSetList, List<String> queryList) throws DataflowException {
        List<Span> matchingResults = new ArrayList<>();
        ListField<Span> payloadField = inputTuple.getField(SchemaConstants.PAYLOAD);
        List<Span> payload = payloadField.getValue();
        Map<Integer, List<Span>> relevantSpansMap = filterRelevantSpans(payload, queryTokenSetList);
        for (String attributeName : attributeNames) {
            AttributeType attributeType = inputTuple.getSchema().getAttribute(attributeName).getAttributeType();
            String fieldValue = inputTuple.getField(attributeName).getValue().toString();

            // types other than TEXT and STRING: throw Exception for now
            if (attributeType != AttributeType.STRING && attributeType != AttributeType.TEXT) {
                throw new DataflowException("KeywordMatcher: Fields other than STRING and TEXT are not supported yet");
            }

            // for STRING type, check if the dictionary entries contains the complete fieldValue
            if (attributeType == AttributeType.STRING) {
                if (queryList.contains(fieldValue)) {
                    Span span = new Span(attributeName, 0, fieldValue.length(), fieldValue, fieldValue);
                    matchingResults.add(span);
                }
            }

            // for TEXT type, every token in the query should be present in span
            if (attributeType == AttributeType.TEXT) {
                for (int index : relevantSpansMap.keySet()) {
                    List<Span> fieldSpanList = relevantSpansMap.get(index).stream().filter(span -> span.getAttributeName().equals(attributeName))
                            .collect(Collectors.toList());
                    if (DataflowUtils.isAllQueryTokensPresent(fieldSpanList, queryTokenSetList.get(index))) {
                        matchingResults.addAll(fieldSpanList);
                    }
                }
            }
        }
        return matchingResults;
    }

    public List<Span> appendPhraseMatchingSpans4Dictionary(Tuple inputTuple, List<String> attributeNames, List<List<String>> queryTokenList, List<Set<String>> queryTokenSetList, List<List<String>> queryTokenListWithStopwords, List<String> queryList) throws DataflowException {
        List<Span> matchingResults = new ArrayList<>();
        ListField<Span> payloadField = inputTuple.getField(SchemaConstants.PAYLOAD);
        List<Span> payload = payloadField.getValue();
        Map<Integer, List<Span>> relevantSpansMap = filterRelevantSpans(payload, queryTokenSetList);
        for (String attributeName : attributeNames) {
            AttributeType attributeType = inputTuple.getSchema().getAttribute(attributeName).getAttributeType();
            String fieldValue = inputTuple.getField(attributeName).getValue().toString();

            // types other than TEXT and STRING: throw Exception for now
            if (attributeType != AttributeType.STRING && attributeType != AttributeType.TEXT) {
                throw new DataflowException("KeywordMatcher: Fields other than STRING and TEXT are not supported yet");
            }

            // for STRING type, the query should match the fieldValue completely
            if (attributeType == AttributeType.STRING) {
                if (queryList.contains(fieldValue)) {
                    Span span = new Span(attributeName, 0, fieldValue.length(), fieldValue, fieldValue);
                    matchingResults.add(span);
                }
            }

            // for TEXT type, spans need to be reconstructed according to the phrase query.
            if (attributeType == AttributeType.TEXT) {
                for (int index : relevantSpansMap.keySet()) {
                    List<Span> fieldSpanList = relevantSpansMap.get(index).stream().filter(span -> span.getAttributeName().equals(attributeName))
                            .collect(Collectors.toList());
                    if (fieldSpanList.isEmpty() || !DataflowUtils.isAllQueryTokensPresent(fieldSpanList, queryTokenSetList.get(index))) {
                        continue;
                    }
                    matchingResults.addAll(DataflowUtils.constructPhraseMatchingSpans(attributeName, fieldValue, queryList.get(index), fieldSpanList, queryTokenListWithStopwords.get(index), queryTokenList.get(index)));
                }
            }
        }
        return matchingResults;
    }

    private Map<Integer, List<Span>> filterRelevantSpans(List<Span> spanList, List<Set<String>> queryTokenSet) {
        Map<Integer, List<Span>> resultMap = new HashMap<>();
        Map<String, List<Integer>> tokenMap = new HashMap<>();
        for (int i = 0; i < queryTokenSet.size(); i++) {
            for (String s : queryTokenSet.get(i)) {
                if (!tokenMap.containsKey(s)) {
                    tokenMap.put(s, new ArrayList<>());
                }
                tokenMap.get(s).add(i);
            }
        }
        Iterator<Span> iterator = spanList.iterator();
        while (iterator.hasNext()) {
            Span span = iterator.next();
            if (tokenMap.keySet().contains(span.getKey())) {
                List<Integer> tokensetIndex = tokenMap.get(span.getKey());
                for (Integer index : tokensetIndex) {
                    if (!resultMap.containsKey(index)) {
                        resultMap.put(index, new ArrayList<>());
                    }
                    resultMap.get(index).add(span);
                }

            }
        }
        return resultMap;
    }

    @Override
    protected void cleanUp() throws TexeraException {

    }

    public Schema getOutputSchema() {
        return outputSchema;
    }

    public void setInputOperator(IOperator inputOperator) {
        this.inputOperator = inputOperator;
    }


    public IOperator getInputOperator() {
        return inputOperator;
    }

    public DictionaryPredicate getPredicate() {
        return this.predicate;
    }

}
