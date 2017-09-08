package com.epam.aerl.mentoring.util;

import com.epam.aerl.mentoring.entity.StudentClassCriteria;
import com.epam.aerl.mentoring.entity.StudentMarksWrapper;
import com.epam.aerl.mentoring.entity.StudentRangeCriteria;
import com.epam.aerl.mentoring.exception.NotCombinedParameterException;
import com.epam.aerl.mentoring.exception.StudentClassCriteriaException;
import com.epam.aerl.mentoring.type.ErrorMessage;
import com.epam.aerl.mentoring.type.GenerationClass;
import org.apache.commons.collections4.MapUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.epam.aerl.mentoring.entity.StudentClassCriteria.createClassCriteriaBuilder;
import static com.epam.aerl.mentoring.entity.StudentMarksWrapper.createMarksWrapperBuilder;
import static com.epam.aerl.mentoring.entity.StudentRangeCriteria.createRangeCriteriaBuilder;

@Service("criteriaAnalyser")
public class CriteriaAnalyser {
    private static final int MIN_INDEX = 0;
    private static final int MAX_INDEX = 1;
    private static final String NOT_COMBINED_PARAMETER_ERR_MSG = "Parameters cannot be combined.";
    private static final String NOT_COMBINED_HAS_CONFLICT_ERR_MSG = "Combined criteria has conflicts.";
    private static final String NOT_NEED_TO_BE_COMBINED = "The entered criteria do not need to be combined.";
    private static final String ATTEMPT_TO_COMBINE = "Attempt to combine the entered criteria.";
    private static final String COMBINED_CRITERIA = "Combined criteria: ";
    private static final String SPLITTER = " - ";
    private static final String RESULT_CRITERIA_STRING = "Result criteria line: ";
    private static final String VALUE_REGEX = "\\d+";
    private static final String CLASS_REGEX = "[a-zA-Z]";
    private static final String WHITESPACE = "\\h*";
    private static final String OPEN_GROUP = "(";
    private static final String CLOSE_GROUP = ")+";
    private static final Logger LOG = LogManager.getLogger(CriteriaAnalyser.class);

    public Map<String, Integer> parse(final String input) {
        Map<String, Integer> result = null;

        if (input != null) {
            result = new HashMap<>();

            Pattern pattern = Pattern.compile(WHITESPACE + OPEN_GROUP + VALUE_REGEX + CLASS_REGEX + CLOSE_GROUP + WHITESPACE);
            Matcher matcher = pattern.matcher(input);

            if (matcher.find()) {
                final String criteria = matcher.group();
                pattern = Pattern.compile(VALUE_REGEX + CLASS_REGEX);
                matcher = pattern.matcher(criteria);

                while (matcher.find()) {
                    final String classCriteria = matcher.group();
                    final String studentClass = parseStudentClass(classCriteria);
                    final Integer studentClassValue = parseStudentClassValue(classCriteria);

                    if (studentClass != null && studentClassValue != null) {
                        result.put(studentClass, studentClassValue);
                    }
                }
            }
        }

        return result;
    }

    public String sortCriteria(final Map<String, Integer> criteria) {
        String resultString = null;

        if (MapUtils.isNotEmpty(criteria)) {
            Map<String, Integer> modifiedMap = new HashMap<>(criteria);
            modifiedMap.remove(GenerationClass.S.toString());

            List<Entry<String, Integer>> list = new LinkedList<>(modifiedMap.entrySet());

            Collections.sort(list, (o1, o2) -> {
                int result = (o2.getValue()).compareTo(o1.getValue());

                if (result == 0) {
                    result = (o1.getKey()).compareTo(o2.getKey());
                }

                return result;
            });

            StringBuilder builder = new StringBuilder();
            builder.append(criteria.get(GenerationClass.S.toString())).append(GenerationClass.S.toString());

            for (Entry<String, Integer> item : list) {
                builder.append(item.getValue()).append(item.getKey());
            }

            resultString = builder.toString();
        }

        return resultString;
    }

    public Map<String, Integer> validate(final Map<String, Integer> parsedCriteria) throws StudentClassCriteriaException {
        Map<String, Integer> result = null;

        if (parsedCriteria != null) {
            if (validateCriteriaValues(parsedCriteria)) {
                result = parsedCriteria;
                addRandClassCriteria(result);
                LOG.debug(NOT_NEED_TO_BE_COMBINED);
            } else {
                LOG.debug(ATTEMPT_TO_COMBINE);
                Map<String, Integer> combined = combinePossibleCriteria(parsedCriteria);

                if (validateCriteriaValues(combined)) {
                    result = combined;
                    LOG.debug(RESULT_CRITERIA_STRING + sortCriteria(result));
                    addRandClassCriteria(result);
                } else {
                    throw new StudentClassCriteriaException(NOT_COMBINED_HAS_CONFLICT_ERR_MSG, ErrorMessage.CONFLICTS_INPUT_CRITERIA.getCode());
                }
            }
        }

        return result;
    }

    private boolean validateCriteriaValues(final Map<String, Integer> criteria) {
        boolean result = false;
        final Integer totalCount = criteria.get(GenerationClass.S.toString());

        if (totalCount != null) {
            int sumCriteria = 0;

            for (Entry<String, Integer> criteriaElement : criteria.entrySet()) {
                if (!criteriaElement.getKey().equals(GenerationClass.S.toString())) {
                    sumCriteria += criteriaElement.getValue();
                }
            }

            result = totalCount - sumCriteria >= 0;
        }

        return result;
    }

    private void addRandClassCriteria(final Map<String, Integer> criteria) {
        final Integer totalCount = criteria.get(GenerationClass.S.toString());

        if (totalCount != null) {
            int sumCriteria = 0;

            for (Entry<String, Integer> criteriaElement : criteria.entrySet()) {
                if (!criteriaElement.getKey().equals(GenerationClass.S.toString())) {
                    sumCriteria += criteriaElement.getValue();
                }
            }

            criteria.put(GenerationClass.RAND.toString().toLowerCase(), totalCount - sumCriteria);
        }
    }

    private Map<String, Integer> combinePossibleCriteria(final Map<String, Integer> criteria) {
        StudentClassCriteriaHolder holder = StudentClassCriteriaHolder.getInstance();
        Map<String, Integer> result = new HashMap<>(criteria);

        for (Entry<String, Integer> criteriaElement : criteria.entrySet()) {
            if (!criteriaElement.getKey().equals(GenerationClass.S.toString())) {
                Entry<String, Integer> appropriate = null;
                StudentClassCriteria combinedClassCriteria = null;

                for (Entry<String, Integer> resultElement : result.entrySet()) {
                    if(!GenerationClass.S.toString().equals(resultElement.getKey()) && !resultElement.getKey().contains(criteriaElement.getKey())) {
                        StudentClassCriteria first = holder.getCriteriaByGenerationClass(criteriaElement.getKey());
                        StudentClassCriteria second = holder.getCriteriaByGenerationClass(resultElement.getKey());

                        try {
                            combinedClassCriteria = combineStudentClassesCriteria(first, second);

                            appropriate = resultElement;
                            break;
                        } catch (NotCombinedParameterException e) {
                            LOG.warn(e);
                        }
                    }
                }

                if (appropriate != null) {
                    Integer oldValue = result.get(criteriaElement.getKey());

                    if (oldValue != null && combinedClassCriteria != null) {
                        String combinedClassCriteriaName = criteriaElement.getKey() + appropriate.getKey();
                        holder.putCombinedStudentClassCriteria(combinedClassCriteriaName, combinedClassCriteria);

                        if (oldValue - appropriate.getValue() == 0) {
                            result.remove(criteriaElement.getKey());
                            result.remove(appropriate.getKey());
                            result.put(combinedClassCriteriaName, oldValue);
                        } else if (oldValue - appropriate.getValue() > 0) {
                            result.put(criteriaElement.getKey(), oldValue - appropriate.getValue());
                            result.remove(appropriate.getKey());
                            result.put(combinedClassCriteriaName, appropriate.getValue());
                        } else {
                            result.put(appropriate.getKey(), appropriate.getValue() - oldValue);
                            result.remove(criteriaElement.getKey());
                            result.put(combinedClassCriteriaName, oldValue);
                        }

                        LOG.debug(COMBINED_CRITERIA + combinedClassCriteriaName + SPLITTER + combinedClassCriteria.toString());
                    }
                }
            }
        }

        return result;
    }

    private StudentClassCriteria combineStudentClassesCriteria(final StudentClassCriteria first, final StudentClassCriteria second)
            throws NotCombinedParameterException {
        StudentClassCriteria result = null;

        if (first != null && second != null) {
            result = createClassCriteriaBuilder()
                    .ageCriteria(combineRangeCriteria(first.getAgeCriteria(), second.getAgeCriteria()))
                    .courseCriteria(combineRangeCriteria(first.getCourseCriteria(), second.getCourseCriteria()))
                    .studentMarksWrapperCriteria(
                            combineMarksWrappers(first.getStudentMarksWrapperCriteria(), second.getStudentMarksWrapperCriteria())
                    ).build();
        }

        return result;
    }

    private StudentMarksWrapper combineMarksWrappers(final StudentMarksWrapper first, final StudentMarksWrapper second) throws NotCombinedParameterException {
        StudentMarksWrapper result = null;

        if (first != null || second != null) {
            if (first == null) {
                result = second;
            } else if (second == null) {
                result = first;
            } else {
                result = createMarksWrapperBuilder()
                        .marksCriteria(
                                combineMapsCriteria(
                                        first.getMarksCriteria(),
                                        second.getMarksCriteria()
                                )
                        ).groupOperationsCriteria(
                                combineMapsCriteria(
                                        first.getGroupOperationsCriteria(),
                                        second.getGroupOperationsCriteria()
                                )
                        )
                        .build();
            }
        }

        return result;
    }

    private <T> Map<T, StudentRangeCriteria> combineMapsCriteria(
            final Map<T, StudentRangeCriteria> first,
            final Map<T, StudentRangeCriteria> second) throws NotCombinedParameterException {
        Map<T, StudentRangeCriteria> result = null;

        if (MapUtils.isNotEmpty(first) || MapUtils.isNotEmpty(second)) {
            if (MapUtils.isEmpty(first)) {
                result = second;
            } else if (MapUtils.isEmpty(second)) {
                result = first;
            } else {
                Map<T, StudentRangeCriteria> search = null;

                if (first.size() <= second.size()) {
                    result = second;
                    search = first;
                } else {
                    result = first;
                    search = second;
                }

                for (Entry<T, StudentRangeCriteria> criteria : search.entrySet()) {
                    if (result.containsKey(criteria.getKey())) {
                        T key = criteria.getKey();
                        result.put(key, combineRangeCriteria(result.get(key), criteria.getValue()));
                    } else {
                        result.put(criteria.getKey(), criteria.getValue());
                    }
                }
            }
        }

        return result;
    }

    private StudentRangeCriteria combineRangeCriteria(final StudentRangeCriteria first, final StudentRangeCriteria second)
            throws NotCombinedParameterException {
        StudentRangeCriteria result = null;

        if (first != null || second != null) {
            if (first == null) {
                result = second;
            } else if (second == null) {
                result = first;
            } else {
                double[] combined = combineRanges(
                        first.getMin(),
                        first.getMax(),
                        second.getMin(),
                        second.getMax());

                if (combined != null) {
                    result = createRangeCriteriaBuilder()
                            .min(combined[MIN_INDEX])
                            .max(combined[MAX_INDEX])
                            .build();
                } else {
                    throw new NotCombinedParameterException(NOT_COMBINED_PARAMETER_ERR_MSG);
                }
            }
        }

        return result;
    }

    private double[] combineRanges(final double firstMin, final double firstMax, final double secondMin, final double secondMax) {
        double[] result = null;

        Double min = null;
        Double max = null;

        if (firstMin <= secondMax && firstMax >= secondMin) {
            min = Math.max(firstMin, secondMin);
            max = Math.min(firstMax, secondMax);
        }

        if (min != null) {
            result = new double[] {min, max};
        }

        return result;
    }

    private String parseStudentClass(final String criteria) {
        String result = null;

        final Matcher matcher = Pattern.compile(CLASS_REGEX).matcher(criteria);

        if (matcher.find()) {
            result = matcher.group();
        }

        return result;
    }

    private Integer parseStudentClassValue(final String criteria) {
        Integer result = null;

        final Matcher matcher = Pattern.compile(VALUE_REGEX).matcher(criteria);

        if (matcher.find()) {
            result = Integer.valueOf(matcher.group());
        }

        return result;
    }
}
