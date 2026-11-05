package com.example.backend.farmacia.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encuentra en la nota que escribe Oncología (texto libre, sin claves) los medicamentos del catálogo y
 * la dosis escrita junto a cada uno. Solo SUGIERE: Farmacia confirma cada clave antes de surtir.
 *
 * Así escriben las notas (Intelisis, octubre de 2026):
 *   "PACLITAXEL 230 MG EN 500 CC DE SOL FIS P/3 HRS"
 *   "DEXAMETASONA 8 MG EN 100 ML DE SOL. FISIOLOGICA P/ 10 MIN"
 *   "METOTREXATE 12.5MG + CITARABINA 50MG + DEXAMETASONA 5 MG"
 *   "APREPITAN 125 MG VO DIA 1, 80 MG DIA 2 Y 3"
 * Lo que va después de "EN", "AFORADO" o "DILUIDO" es el vehículo (la solución donde se prepara), no
 * otro medicamento de la indicación. Los nombres a veces vienen sin la última letra (APREPITAN,
 * METOTREXATE), por eso se aceptan si solo difieren al final.
 */
final class LectorNotaQuimio {

    /** Un medicamento del catálogo reducido a las palabras de su fármaco. */
    record Farmaco(String clave, List<String> palabras) {
    }

    /** Un fármaco hallado en la nota: el texto del renglón, el fármaco y la dosis escrita (si la hay). */
    record Hallazgo(String texto, String farmaco, BigDecimal dosisCantidad, String dosisUnidad, List<String> claves) {
    }

    // Palabras que no son fármaco: se brincan al armar el nombre (CLORURO DE SODIO → CLORURO SODIO).
    private static final Set<String> VACIAS = Set.of("DE", "DEL", "LA", "EL", "Y", "CON", "AL", "EN", "PARA");
    // Si el nombre del catálogo empieza con estas, el fármaco necesita la palabra siguiente (ACIDO FOLICO).
    private static final Set<String> GENERICAS = Set.of("ACIDO", "CLORURO", "SULFATO", "FOSFATO", "BICARBONATO", "GLUCONATO",
            "CITRATO", "HIDROXIDO", "CARBONATO", "OXIDO", "SOLUCION", "AGUA", "COMPLEJO", "VITAMINA", "INMUNOGLOBULINA", "HIERRO");
    // Nombres del catálogo que empiezan con la forma farmacéutica (o que no son medicamento, como los
    // medios de cultivo): no son un fármaco que el médico escriba en la nota.
    private static final Set<String> FORMAS = Set.of("TABLETA", "TABLETAS", "CAPSULA", "CAPSULAS", "GRAGEA", "COMPRIMIDO", "AMPOLLETA",
            "FRASCO", "JERINGA", "ENVASE", "CREMA", "SUSPENSION", "INYECTABLE", "POLVO", "PARCHE", "UNGUENTO", "JARABE", "CULTIVO");
    // Después de estas palabras viene el vehículo de la mezcla.
    private static final Set<String> VEHICULO = Set.of("EN", "AFORADO", "AFORADOS", "AFORADA", "AFORAR", "DILUIDO", "DILUIDOS", "DILUIR");
    // Renglón que solo dice la dosis del medicamento de arriba: "Administrar 1 g IV".
    private static final Pattern SOLO_DOSIS = Pattern.compile("^(ADMINISTRAR|APLICAR|DOSIS|PASAR|DAR)\\b.*");
    private static final int MINIMO_PALABRA = 5;
    // Desde esta longitud se acepta una letra de más, de menos, cambiada o dos letras volteadas.
    private static final int LARGO_PARA_ERRATAS = 7;
    private static final Pattern DOSIS = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(MCG|MG|GRAMOS|GRAMO|GR|G|UI|ML)(\\s*/\\s*M2|\\s*/\\s*KG)?\\b");

    /** Dónde está un fármaco en las palabras de un segmento. exacta = sin errores de escritura. */
    private record Coincidencia(String farmaco, int inicio, int fin, boolean exacta) {
    }

    private LectorNotaQuimio() {
    }

    /** Las palabras del fármaco de un nombre del catálogo ("PACLITAXEL, SOLUCIÓN…" → [PACLITAXEL]). */
    static List<String> palabrasFarmaco(String nombreGenerico) {
        String cabeza = normalizar(nombreGenerico == null ? "" : nombreGenerico).split("[.,(;:]")[0];
        List<String> palabras = Arrays.stream(cabeza.split("[^A-Z]+")).filter(p -> !p.isEmpty() && !VACIAS.contains(p)).toList();
        if (palabras.isEmpty() || palabras.get(0).length() < MINIMO_PALABRA - 1 || FORMAS.contains(palabras.get(0))) {
            return List.of();
        }
        if (GENERICAS.contains(palabras.get(0)) && palabras.size() > 1) {
            return List.of(palabras.get(0), palabras.get(1));
        }
        return List.of(palabras.get(0));
    }

    /** Los fármacos que aparecen en la nota, en el orden en que los escribió el médico. */
    static List<Hallazgo> leer(String nota, List<Farmaco> catalogo) {
        List<Hallazgo> hallazgos = new ArrayList<>();
        if (nota == null || nota.isBlank()) {
            return hallazgos;
        }
        // Clave por fármaco ("PACLITAXEL" → claves de paclitaxel), para no repetir el mismo fármaco.
        Map<String, List<String>> porFarmaco = new LinkedHashMap<>();
        for (Farmaco f : catalogo) {
            if (!f.palabras().isEmpty()) {
                porFarmaco.computeIfAbsent(String.join(" ", f.palabras()), k -> new ArrayList<>()).add(f.clave());
            }
        }
        // Fármaco → posición en "hallazgos", para no repetirlo (si primero se menciona sin dosis
        // y luego con dosis, se queda el renglón con dosis).
        Map<String, Integer> yaHallados = new LinkedHashMap<>();
        List<String> renglones = Arrays.stream(nota.split("\\r?\\n")).map(String::strip).filter(r -> !r.isEmpty()).toList();
        for (int r = 0; r < renglones.size(); r++) {
            String original = renglones.get(r);
            // Un renglón puede traer varios medicamentos: "METOTREXATE 12.5MG + CITARABINA 50MG".
            for (String segmento : normalizar(original).split("[+;]|\\s-\\s|^-")) {
                List<String> palabras = Arrays.stream(segmento.split("[^A-Z0-9]+")).filter(p -> !p.isEmpty()).toList();
                int finActivo = palabras.size();
                for (int i = 1; i < palabras.size(); i++) {
                    if (VEHICULO.contains(palabras.get(i))) {
                        finActivo = i;
                        break;
                    }
                }
                List<String> activas = palabras.subList(0, finActivo);
                for (Coincidencia c : coincidencias(activas, porFarmaco.keySet())) {
                    BigDecimal[] cantidad = new BigDecimal[1];
                    String unidad = dosisDespuesDe(segmento, activas.get(c.inicio()), cantidad);
                    // "Paracetamol solución inyectable" y abajo "Administrar 1 g IV".
                    if (unidad == null && r + 1 < renglones.size() && SOLO_DOSIS.matcher(normalizar(renglones.get(r + 1))).matches()) {
                        unidad = dosisDespuesDe(normalizar(renglones.get(r + 1)), "", cantidad);
                    }
                    Hallazgo nuevo = new Hallazgo(original, c.farmaco(), cantidad[0], unidad, porFarmaco.get(c.farmaco()));
                    Integer previo = yaHallados.get(c.farmaco());
                    if (previo == null) {
                        yaHallados.put(c.farmaco(), hallazgos.size());
                        hallazgos.add(nuevo);
                    } else if (hallazgos.get(previo).dosisUnidad() == null && unidad != null) {
                        hallazgos.set(previo, nuevo);
                    }
                }
            }
        }
        return hallazgos;
    }

    /**
     * Los fármacos de un segmento en el orden escrito, sin encimarse: en "CLORURO DE SODIO" se queda
     * CLORURO SODIO y no también SODIO. En el mismo lugar gana el que está bien escrito y el más largo.
     */
    private static List<Coincidencia> coincidencias(List<String> palabras, Iterable<String> farmacos) {
        List<Coincidencia> todas = new ArrayList<>();
        for (String farmaco : farmacos) {
            Coincidencia c = buscar(palabras, farmaco);
            if (c != null) {
                todas.add(c);
            }
        }
        todas.sort(Comparator.comparingInt(Coincidencia::inicio)
                .thenComparing(c -> !c.exacta())
                .thenComparing(c -> -(c.fin() - c.inicio())));
        List<Coincidencia> aceptadas = new ArrayList<>();
        int ocupadoHasta = -1;
        for (Coincidencia c : todas) {
            if (c.inicio() > ocupadoHasta) {
                aceptadas.add(c);
                ocupadoHasta = c.fin();
            }
        }
        return aceptadas;
    }

    /** La primera vez que todas las palabras del fármaco aparecen en orden; null si no aparece. */
    private static Coincidencia buscar(List<String> palabras, String farmaco) {
        String[] buscadas = farmaco.split(" ");
        for (int i = 0; i < palabras.size(); i++) {
            if (!parecida(palabras.get(i), buscadas[0])) {
                continue;
            }
            boolean exacta = palabras.get(i).equals(buscadas[0]);
            int j = i + 1;
            int fin = i;
            boolean todas = true;
            for (int k = 1; k < buscadas.length; k++) {
                while (j < palabras.size() && VACIAS.contains(palabras.get(j))) {
                    j++;
                }
                if (j >= palabras.size() || !parecida(palabras.get(j), buscadas[k])) {
                    todas = false;
                    break;
                }
                exacta &= palabras.get(j).equals(buscadas[k]);
                fin = j;
                j++;
            }
            if (todas) {
                return new Coincidencia(farmaco, i, fin, exacta);
            }
        }
        return null;
    }

    /**
     * Iguales; o la más corta (de 6 letras o más) solo difiere en la última letra (APREPITAN ≈
     * APREPITANT); o, en nombres largos, una sola errata: una letra de más, de menos o cambiada, o dos
     * letras volteadas (PEMBROLIZIMAB, DEFENHIDRAMINA, VINCRSITINA).
     */
    static boolean parecida(String escrita, String catalogo) {
        if (escrita.equals(catalogo)) {
            return true;
        }
        if (Math.min(escrita.length(), catalogo.length()) >= LARGO_PARA_ERRATAS && unaErrata(escrita, catalogo)) {
            return true;
        }
        boolean escritaEsCorta = escrita.length() <= catalogo.length();
        String corta = escritaEsCorta ? escrita : catalogo;
        String larga = escritaEsCorta ? catalogo : escrita;
        if (corta.length() < 6 || larga.length() - corta.length() > 1) {
            return false;
        }
        int comunes = 0;
        while (comunes < corta.length() && corta.charAt(comunes) == larga.charAt(comunes)) {
            comunes++;
        }
        return comunes >= corta.length() - 1 && comunes >= larga.length() - 2;
    }

    /** Distancia de edición (con letras volteadas) igual a 1. */
    private static boolean unaErrata(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) {
            return false;
        }
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + costo);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()] <= 1;
    }

    /** La primera dosis escrita después del fármaco ("PACLITAXEL 230 MG" → 230 mg). mg/m2 no es la dosis total. */
    private static String dosisDespuesDe(String segmento, String palabra, BigDecimal[] cantidad) {
        int desde = segmento.indexOf(palabra);
        Matcher m = DOSIS.matcher(desde < 0 ? segmento : segmento.substring(desde));
        if (!m.find() || m.group(3) != null) {
            return null;
        }
        cantidad[0] = new BigDecimal(m.group(1).replace(',', '.'));
        return unidad(m.group(2));
    }

    /** "300 mg/50 ml" → 300 mg: lo que trae cada frasco según la concentración del catálogo. */
    static String contenido(String concentracion, BigDecimal[] cantidad) {
        if (concentracion == null) {
            return null;
        }
        Matcher m = DOSIS.matcher(normalizar(concentracion));
        if (!m.find()) {
            return null;
        }
        cantidad[0] = new BigDecimal(m.group(1).replace(',', '.'));
        return unidad(m.group(2));
    }

    private static String unidad(String escrita) {
        return switch (escrita) {
            case "MCG" -> "mcg";
            case "G", "GR", "GRAMO", "GRAMOS" -> "g";
            case "UI" -> "UI";
            case "ML" -> "ml";
            default -> "mg";
        };
    }

    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }
}
