package com.pavelvoronin.pz3dLoader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
/** Derived from ZombieBuddy PatchEngine at a403daf; see LICENSE.txt. */
final class AdviceMatcher {
    private static final List<Class<? extends Annotation>> ADVICE_ANNOTATION_TYPES = List.of(
            Advice.OnMethodEnter.class,
            Advice.OnMethodExit.class
            );

    // arguments are "non-special"
    private static final HashSet<Class<? extends Annotation>> ARGUMENT_ANNOTATIONS = new HashSet<>(List.of(
            Advice.Argument.class,
            net.bytebuddy.implementation.bind.annotation.Argument.class
            ));

    private static boolean isSpecialAnnotation(Annotation ann) {
        var annType = ann.annotationType();
        if (ARGUMENT_ANNOTATIONS.contains(annType)) {
            return false;
        }

        String annTypeName = annType.getName();
        boolean result = annTypeName.startsWith("net.bytebuddy.implementation.bind.annotation.") || annTypeName.startsWith("net.bytebuddy.asm.Advice");
        // Logger.trace("isSpecialAnnotation: " + ann + " -> " + result);
        return result;
    }

    private static boolean hasAnnotation(Method method, List<Class<? extends Annotation>> annTypes) {
        for (var annType : annTypes) {
            if (method.isAnnotationPresent(annType)) {
                return true;
            }
        }
        return false;
    }

    private static Method findMethodWithAnnotation(Class<?> clazz, Class<? extends Annotation> annType) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(annType)) {
                return method;
            }
        }
        return null;
    }

    private static boolean hasParameterAnnotation(Method method, Class<? extends Annotation> annType) {
        Annotation[][] paramAnns = method.getParameterAnnotations();
        for (Annotation[] paramAnn : paramAnns) {
            for (Annotation ann : paramAnn) {
                if (annType.isInstance(ann)) {
                    return true;
                }
            }
        }
        return false;
    }

    static net.bytebuddy.matcher.ElementMatcher.Junction<MethodDescription> buildAdviceMethodMatcher(
            String methodName,
            boolean strictMatch,
            Class<?> patchClass,
            Method onlyMethod) {
        var methodMatcher = SyntaxSugar.methodMatcher(methodName);

        boolean hasAllArguments = false;
        boolean hasNoParamMethod = false;
        List<Class<?>> inferredTypes = null;
        Integer minParameterCount = null;
        List<Map<Integer, Class<?>>> allAdviceMaps = new ArrayList<>();
        List<Boolean> allAdviceExactMatch = new ArrayList<>();
        Set<List<Class<?>>> allInferredSignatures = new HashSet<>();

        for (Method adviceMethod : patchClass.getDeclaredMethods()) {
            if (onlyMethod != null && adviceMethod != onlyMethod) {
                continue;
            }

            if (!hasAnnotation(adviceMethod, ADVICE_ANNOTATION_TYPES)) {
                continue;
            }

            Annotation[][] paramAnns = adviceMethod.getParameterAnnotations();

            if (hasParameterAnnotation(adviceMethod, Advice.AllArguments.class)) {
                hasAllArguments = true;
            }

            if (adviceMethod.getParameterCount() == 0 && !hasAllArguments) {
                hasNoParamMethod = true;
            }

            if (!hasAllArguments) {
                Class<?>[] paramTypes = adviceMethod.getParameterTypes();
                Map<Integer, Class<?>> argumentMap = new HashMap<>();
                boolean hasAnyArguments = false;
                boolean allParamsAreSpecial = paramTypes.length > 0;

                for (int i = 0; i < paramAnns.length; i++) {
                    boolean isArgument = false;
                    boolean skip = false;
                    int argumentIndex = -1;

                    for (Annotation ann : paramAnns[i]) {
                        if (ann instanceof Advice.Argument arg) {
                            isArgument = true;
                            hasAnyArguments = true;
                            allParamsAreSpecial = false;
                            argumentIndex = arg.value();
                            Class<?> paramType = paramTypes[i];
                            Class<?> typeToStore = paramType;
                            argumentMap.put(argumentIndex, typeToStore);
                            break;
                        }

                        if (isSpecialAnnotation(ann)) {
                            skip = true;
                            break;
                        }
                    }

                    if (!skip && !isArgument) {
                        allParamsAreSpecial = false;
                        argumentMap.put(i, paramTypes[i]);
                    }
                }

                if (!argumentMap.isEmpty()) {
                    allAdviceMaps.add(argumentMap);
                    allAdviceExactMatch.add(!hasAnyArguments);
                }

                if (allParamsAreSpecial && paramTypes.length > 0) {
                    hasNoParamMethod = true;
                }

                if (hasAnyArguments && !argumentMap.isEmpty()) {
                    int maxIndex = argumentMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
                    boolean hasCompleteSequence = true;
                    for (int idx = 0; idx <= maxIndex; idx++) {
                        if (!argumentMap.containsKey(idx)) {
                            hasCompleteSequence = false;
                            break;
                        }
                    }

                    int requiredParamCount = maxIndex + 1;
                    if (minParameterCount == null || requiredParamCount > minParameterCount) {
                        minParameterCount = requiredParamCount;
                    }

                    if (hasCompleteSequence) {
                        List<Class<?>> sig = new ArrayList<>();
                        for (int idx = 0; idx <= maxIndex; idx++) {
                            sig.add(argumentMap.get(idx));
                        }
                        allInferredSignatures.add(sig);
                    }
                } else if (!argumentMap.isEmpty()) {
                    List<Class<?>> sig = new ArrayList<>();
                    for (int idx = 0; idx < paramTypes.length; idx++) {
                        boolean isSpecial = false;
                        for (Annotation ann : paramAnns[idx]) {
                            if (isSpecialAnnotation(ann)) {
                                isSpecial = true;
                                break;
                            }
                        }
                        if (!isSpecial) {
                            sig.add(paramTypes[idx]);
                        }
                    }
                    if (!sig.isEmpty()) {
                        allInferredSignatures.add(sig);
                        if (inferredTypes == null) {
                            inferredTypes = sig;
                        }
                    }
                }
            }
        }

        if (allInferredSignatures.size() > 1) {
            inferredTypes = null;
        }

        if (hasAllArguments) {
            return methodMatcher;
        }

        if (hasNoParamMethod && !strictMatch && allAdviceMaps.isEmpty()) {
            return methodMatcher;
        }

        final List<Map<Integer, Class<?>>> maps = new ArrayList<>(allAdviceMaps);
        final List<Boolean> exactMatches = new ArrayList<>(allAdviceExactMatch);
        final int minParams = (minParameterCount != null) ? minParameterCount : 0;
        final boolean strict = strictMatch;
        final boolean noParam = hasNoParamMethod;

        return methodMatcher.and(new net.bytebuddy.matcher.ElementMatcher<MethodDescription>() {
            @Override
            public boolean matches(MethodDescription target) {
                int targetParamCount = target.getParameters().size();
                boolean allArgsMatch = false;
                boolean result = false;

                while (true) {
                    if (noParam && targetParamCount == 0) {
                        result = true;
                        break;
                    }
                    if (strict && noParam && maps.isEmpty() && targetParamCount > 0) {
                        result = false;
                        break;
                    }

                    for (int i = 0; i < maps.size(); i++) {
                        Map<Integer, Class<?>> argMap = maps.get(i);
                        boolean exact = exactMatches.get(i);

                        if (exact && targetParamCount != argMap.size()) continue;
                        if (!exact && targetParamCount < argMap.size()) continue;
                        if (!exact && targetParamCount < minParams) continue;

                        allArgsMatch = true;
                        for (Map.Entry<Integer, Class<?>> entry : argMap.entrySet()) {
                            int idx = entry.getKey();
                            if (idx >= targetParamCount) {
                                allArgsMatch = false;
                                break;
                            }
                            Class<?> expected = entry.getValue();
                            if (expected == Object.class) continue;
                            net.bytebuddy.description.type.TypeDescription actual = target.getParameters().get(idx).getType().asErasure();
                            if (!actual.isAssignableTo(expected)) {
                                allArgsMatch = false;
                                break;
                            }
                        }
                        if (allArgsMatch) break;
                    }
                    break;
                }

                return result || allArgsMatch || (noParam && !strict && minParams == 0);
            }
        });
    }
}
