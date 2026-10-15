package com.agentic.orchestrator.analysis;

import com.agentic.orchestrator.util.Md;
import com.agentic.orchestrator.workspace.Workspace;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Codebase reasoning for the brownfield scenario. Parses the Java sources of the workspace with
 * JavaParser, builds a model of controllers, endpoints, entities, services and repositories, and
 * maps the concerns of a requirement (expiry, rate limiting ...) onto the impacted components.
 *
 * <p>The mapping from requirement to impact is rule-based (keyword to concern to component
 * kind). It is deliberately transparent and auditable; a model-based impact analysis could be
 * plugged in behind the same {@link ImpactReport}.
 */
public class ImpactAnalyzer {
    public record Endpoint(String method, String path, String handler, String file) { }

    public record ClassInfo(String name, String kind, String file, List<String> details) { }

    public record Impacted(String component, String reason) { }

    /** Structure of the code in the workspace. */
    public record CodeModel(List<ClassInfo> classes, List<Endpoint> endpoints, List<String> warnings) {
        public List<ClassInfo> ofKind(String kind) {
            return classes.stream().filter(c -> c.kind().equals(kind)).toList();
        }
    }

    /** Result of assessing a requirement against the code. */
    public record ImpactReport(CodeModel model, List<Impacted> impacted, List<String> concerns,
                               boolean schemaChange, boolean apiChange) {
        public String toMarkdown() {
            StringBuilder sb = new StringBuilder("# Impact analysis\n\n");
            sb.append("Concerns detected in the requirement: ")
                    .append(concerns.isEmpty() ? "_none recognised_" : String.join(", ", concerns)).append("\n\n");
            sb.append("## Code structure (parsed with JavaParser)\n\n");
            sb.append(Md.table(List.of("Kind", "Class", "File", "Details"),
                    model.classes().stream().map(c -> List.of(c.kind(), c.name(), c.file(),
                            String.join(", ", c.details()))).toList())).append('\n');
            sb.append("## Endpoints\n\n");
            sb.append(Md.table(List.of("Method", "Path", "Handler"),
                    model.endpoints().stream().map(e -> List.of(e.method(), e.path(), e.handler())).toList()))
                    .append('\n');
            sb.append("## Impacted components\n\n");
            sb.append(Md.table(List.of("Component", "Why it is impacted"),
                    impacted.stream().map(i -> List.of(i.component(), i.reason())).toList())).append('\n');
            sb.append("## Change classification\n\n");
            sb.append("- Database schema change: **").append(schemaChange ? "yes" : "no").append("**\n");
            sb.append("- Public API contract change: **").append(apiChange ? "yes" : "no").append("**\n");
            if (!model.warnings().isEmpty()) {
                sb.append("\n## Analysis warnings\n\n").append(Md.bullets(model.warnings()));
            }
            return sb.toString();
        }
    }

    private static final Map<String, String> HTTP_ANNOTATIONS = Map.of(
            "GetMapping", "GET", "PostMapping", "POST", "PutMapping", "PUT",
            "DeleteMapping", "DELETE", "PatchMapping", "PATCH");

    /** Parses every Java file under src/main/java. */
    public CodeModel scan(Workspace ws) {
        JavaParser parser = new JavaParser(new ParserConfiguration().setLanguageLevel(languageLevel()));
        List<ClassInfo> classes = new ArrayList<>();
        List<Endpoint> endpoints = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (String file : ws.list("src/main/java/**/*.java")) {
            ParseResult<CompilationUnit> result = parser.parse(ws.read(file));
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                warnings.add(file + " could not be fully parsed by the analyzer");
                if (result.getResult().isEmpty()) {
                    continue;
                }
            }
            CompilationUnit cu = result.getResult().get();
            for (ClassOrInterfaceDeclaration c : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                String kind = kindOf(c);
                List<String> details = new ArrayList<>();
                switch (kind) {
                    case "controller" -> collectEndpoints(c, file, endpoints, details);
                    case "entity" -> {
                        for (FieldDeclaration f : c.getFields()) {
                            f.getVariables().forEach(v -> details.add(v.getNameAsString()));
                        }
                    }
                    case "service" -> {
                        for (MethodDeclaration m : c.getMethods()) {
                            if (m.isPublic()) {
                                details.add(m.getNameAsString() + "()");
                            }
                        }
                    }
                    default -> { }
                }
                classes.add(new ClassInfo(c.getNameAsString(), kind, file, details));
            }
        }
        return new CodeModel(classes, endpoints, warnings);
    }

    /** Scans the workspace and maps the requirement onto the impacted components. */
    public ImpactReport analyze(Workspace ws, String requirement) {
        return assess(scan(ws), requirement);
    }

    public ImpactReport assess(CodeModel model, String requirement) {
        String low = requirement.toLowerCase(Locale.ROOT);
        List<String> concerns = new ArrayList<>();
        List<Impacted> impacted = new ArrayList<>();
        boolean schema = false;
        boolean api = false;

        if (low.contains("expir") || low.contains("ttl")) {
            concerns.add("expiry");
            for (ClassInfo e : model.ofKind("entity")) {
                if (e.name().toLowerCase(Locale.ROOT).contains("link")) {
                    impacted.add(new Impacted(e.name(), "gains an expires_at attribute and an isExpired check"));
                    schema = true;
                }
            }
            for (ClassInfo s : model.ofKind("service")) {
                impacted.add(new Impacted(s.name(), "create must accept an expiry; resolve and get must enforce it"));
            }
            for (ClassInfo c : model.ofKind("controller")) {
                impacted.add(new Impacted(c.name(), "request and response contract gains expiresAt; 410 Gone for expired links"));
                api = true;
            }
            for (ClassInfo h : model.ofKind("error-handler")) {
                impacted.add(new Impacted(h.name(), "must map the new LinkExpired condition to HTTP 410"));
            }
        }
        if (low.contains("rate limit") || low.contains("rate-limit") || low.contains("throttl")) {
            concerns.add("rate-limiting");
            for (ClassInfo c : model.ofKind("controller")) {
                impacted.add(new Impacted(c.name(), "all endpoints are protected by a new servlet filter (cross-cutting, no code change inside)"));
            }
            for (ClassInfo h : model.ofKind("error-handler")) {
                impacted.add(new Impacted(h.name(), "problem-details format is reused for HTTP 429 responses"));
            }
            api = true;   // new 429 response + Retry-After header become part of the contract
        }
        if (concerns.isEmpty()) {
            for (ClassInfo c : model.classes()) {
                if (low.contains(c.name().toLowerCase(Locale.ROOT))) {
                    impacted.add(new Impacted(c.name(), "named explicitly in the requirement"));
                }
            }
        }
        return new ImpactReport(model, mergeByComponent(impacted), concerns, schema, api);
    }

    private static String kindOf(ClassOrInterfaceDeclaration c) {
        if (c.isAnnotationPresent("RestController") || c.isAnnotationPresent("Controller")) {
            return "controller";
        }
        if (c.isAnnotationPresent("RestControllerAdvice") || c.isAnnotationPresent("ControllerAdvice")) {
            return "error-handler";
        }
        if (c.isAnnotationPresent("Entity")) {
            return "entity";
        }
        if (c.isAnnotationPresent("Service")) {
            return "service";
        }
        if (c.isAnnotationPresent("Repository")
                || c.getExtendedTypes().stream().anyMatch(t -> t.getNameAsString().endsWith("Repository"))) {
            return "repository";
        }
        if (c.isAnnotationPresent("Component")) {
            return "component";
        }
        return "class";
    }

    private static void collectEndpoints(ClassOrInterfaceDeclaration c, String file,
                                         List<Endpoint> endpoints, List<String> details) {
        String base = c.getAnnotationByName("RequestMapping").map(ImpactAnalyzer::pathOf).orElse("");
        for (MethodDeclaration m : c.getMethods()) {
            for (AnnotationExpr a : m.getAnnotations()) {
                String verb = HTTP_ANNOTATIONS.get(a.getNameAsString());
                if (verb != null) {
                    String path = base + pathOf(a);
                    endpoints.add(new Endpoint(verb, path.isEmpty() ? "/" : path, c.getNameAsString() + "." + m.getNameAsString(), file));
                    details.add(verb + " " + (path.isEmpty() ? "/" : path));
                }
            }
        }
    }

    private static String pathOf(AnnotationExpr a) {
        if (a.isSingleMemberAnnotationExpr()) {
            return literal(a.asSingleMemberAnnotationExpr().getMemberValue());
        }
        if (a.isNormalAnnotationExpr()) {
            for (MemberValuePair p : a.asNormalAnnotationExpr().getPairs()) {
                String n = p.getNameAsString();
                if (n.equals("value") || n.equals("path")) {
                    return literal(p.getValue());
                }
            }
        }
        return "";
    }

    private static String literal(Expression e) {
        return e.isStringLiteralExpr() ? e.asStringLiteralExpr().asString() : e.toString();
    }

    /** Highest Java language level the bundled JavaParser supports (records need 16+). */
    static ParserConfiguration.LanguageLevel languageLevel() {
        for (String name : new String[]{"JAVA_21", "JAVA_17", "JAVA_16", "JAVA_15"}) {
            try {
                return ParserConfiguration.LanguageLevel.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // try the next lower level
            }
        }
        return ParserConfiguration.LanguageLevel.POPULAR;
    }

    /** Convenience for tests and docs. */
    static Optional<ClassInfo> find(CodeModel m, String name) {
        return m.classes().stream().filter(c -> c.name().equals(name)).findFirst();
    }

    /** One row per component: several concerns affecting the same class are listed together. */
    private static List<Impacted> mergeByComponent(List<Impacted> impacted) {
        java.util.Map<String, List<String>> reasons = new java.util.LinkedHashMap<>();
        for (Impacted i : impacted) {
            List<String> r = reasons.computeIfAbsent(i.component(), k -> new ArrayList<>());
            if (!r.contains(i.reason())) {
                r.add(i.reason());
            }
        }
        List<Impacted> merged = new ArrayList<>();
        reasons.forEach((component, rs) -> merged.add(new Impacted(component, String.join("; ", rs))));
        return merged;
    }
}
