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

    /**
     * Un fármaco hallado en la nota: el texto del renglón, el fármaco y la dosis escrita (si la hay).
     * soloSiNoHay: va después de "EN CASO DE DESABASTO (DE X) DAR…", "SI NO HAY…" o "EN SU DEFECTO": es una
     * alternativa que solo se da si falta lo indicado (siNoHayDe = lo que falta, si el médico lo escribió).
     */
    record Hallazgo(String texto, String farmaco, BigDecimal dosisCantidad, String dosisUnidad, List<String> claves,
                    boolean soloSiNoHay, String siNoHayDe, boolean solucion, int veces) {
        Hallazgo(String texto, String farmaco, BigDecimal dosisCantidad, String dosisUnidad, List<String> claves) {
            this(texto, farmaco, dosisCantidad, dosisUnidad, claves, false, null, false, 1);
        }

        Hallazgo(String texto, String farmaco, BigDecimal dosisCantidad, String dosisUnidad, List<String> claves,
                 boolean soloSiNoHay, String siNoHayDe) {
            this(texto, farmaco, dosisCantidad, dosisUnidad, claves, soloSiNoHay, siNoHayDe, false, 1);
        }
    }

    // Soluciones que Farmacia también entrega: el diluyente de cada mezcla ("en 500 ml de sol. cloruro de
    // sodio") y los lavados de vena. Se juntan por solución y volumen (solucion = true, veces = bolsas).
    private static final Map<String, Pattern> SOLUCIONES = new LinkedHashMap<>();

    static {
        SOLUCIONES.put("CLORURO SODIO", Pattern.compile("\\b(CLORURO|NACL|SALINA|FISIOLOGICA|FIS|SS|SSN|SF)\\b"));
        SOLUCIONES.put("GLUCOSA", Pattern.compile("\\b(GLUCOSA|GLUCOSADA|DEXTROSA|SG|SG5|SGL)\\b"));
        SOLUCIONES.put("SOLUCION HARTMANN", Pattern.compile("\\bHARTMANN\\b"));
        SOLUCIONES.put("AGUA INYECTABLE", Pattern.compile("\\bAGUA\\b"));
    }

    private static final Pattern VOLUMEN = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(ML|CC)\\b");

    // Renglón que abre las alternativas por desabasto: "EN CASO DE DESABASTO DE PALONOSETRON DAR", "SI NO HAY…".
    private static final Pattern MARCA_ALTERNATIVA = Pattern.compile(
            "\\b(EN CASO DE (DESABASTO|NO (HABER|HAY|CONTAR|TENER|EXISTIR))|SI NO (HAY|SE CUENTA|CONTAMOS)|EN SU DEFECTO|DE NO (HABER|CONTAR)|A FALTA DE|EN AUSENCIA DE)\\b");
    // Lo que falta, escrito después de la marca: "DESABASTO DE NETUPITANT/ PALONOSETRON DAR" → NETUPITANT/ PALONOSETRON.
    private static final Pattern FALTA_DE = Pattern.compile(
            "\\b(?:DESABASTO|NO HAY|NO HABER|NO CONTAR CON|FALTA|AUSENCIA)\\s+(?:DE\\s+)?([A-Z0-9/ ]{3,60}?)\\s*(?:,|\\bDAR\\b|\\bADMINISTRAR\\b|\\bUSAR\\b|\\bSE DA\\b|$)");
    // Desde aquí, en el mismo renglón de la marca, va la alternativa.
    private static final Pattern DAR = Pattern.compile("\\b(DAR|ADMINISTRAR|USAR|SE DA|CAMBIAR A|SUSTITUIR POR)\\b");

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
    // "CADA <pieza> CONTIENE: <lo que trae>" hasta "ENVASE CON…" (o el final).
    private static final Pattern CADA_CONTIENE = Pattern.compile("\\bCADA\\s+(.{1,60}?)\\s+CONTIENEN?\\b:?(.*?)(?:\\bENVASE\\b|$)");
    // Lo que no es una pieza: volumen o peso ("ML", "100 G"), el envase o la caja, o una dosis de inhalador.
    private static final Pattern NO_ES_PIEZA = Pattern.compile(
            "^(\\d+(?:[.,]\\d+)?\\s*)?(ML|MILILITROS?|L|LITROS?|G|GR|GRAMOS?|MG|ENVASE|CAJA|DOSIS|DISPARO|ATOMIZACION|GOTA|GOTAS|CM|CENTIMETROS?)\\b.*");
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
        // Se conservan los renglones vacíos: un renglón en blanco cierra el bloque de alternativas.
        List<String> renglones = Arrays.stream(nota.split("\\r?\\n")).map(String::strip).toList();
        boolean enAlternativas = false;
        String siNoHayDe = null;
        Map<String, Hallazgo> soluciones = new LinkedHashMap<>();
        for (int r = 0; r < renglones.size(); r++) {
            String original = renglones.get(r);
            if (original.isEmpty()) {
                enAlternativas = false;
                siNoHayDe = null;
                continue;
            }
            String linea = normalizar(original);
            Matcher marca = MARCA_ALTERNATIVA.matcher(linea);
            if (marca.find()) {
                // Desde aquí lo que sigue es la alternativa; en este mismo renglón, solo lo que va después de "DAR".
                enAlternativas = true;
                Matcher falta = FALTA_DE.matcher(linea);
                siNoHayDe = falta.find() ? falta.group(1).replaceAll("[\\s/]+$", "").replaceAll("\\s+", " ").trim() : null;
                if (siNoHayDe != null && siNoHayDe.isEmpty()) {
                    siNoHayDe = null;
                }
                Matcher dar = DAR.matcher(linea);
                linea = dar.find(marca.end()) ? linea.substring(dar.end()) : "";
            }
            // Un renglón puede traer varios medicamentos: "METOTREXATE 12.5MG + CITARABINA 50MG".
            for (String segmento : linea.split("[+;]|\\s-\\s|^-")) {
                List<String> palabras = Arrays.stream(segmento.split("[^A-Z0-9]+")).filter(p -> !p.isEmpty()).toList();
                int finActivo = palabras.size();
                for (int i = 1; i < palabras.size(); i++) {
                    if (VEHICULO.contains(palabras.get(i))) {
                        finActivo = i;
                        break;
                    }
                }
                List<String> activas = palabras.subList(0, finActivo);
                // El diluyente de la mezcla: "… EN 500 ML DE SOLUCION DE CLORURO DE SODIO 0.9%".
                if (finActivo < palabras.size() && !enAlternativas) {
                    int desde = segmento.indexOf(" " + palabras.get(finActivo) + " ");
                    String vehiculo = desde < 0 ? "" : segmento.substring(desde);
                    for (Map.Entry<String, Pattern> s : SOLUCIONES.entrySet()) {
                        if (porFarmaco.containsKey(s.getKey()) && s.getValue().matcher(vehiculo).find()) {
                            Matcher v = VOLUMEN.matcher(vehiculo);
                            acumularSolucion(soluciones, s.getKey(), v.find() ? new BigDecimal(v.group(1).replace(',', '.')) : null,
                                    original, porFarmaco.get(s.getKey()));
                            break;
                        }
                    }
                }
                for (Coincidencia c : coincidencias(activas, porFarmaco.keySet())) {
                    BigDecimal[] cantidad = new BigDecimal[1];
                    String unidad = dosisDespuesDe(segmento, activas.get(c.inicio()), cantidad);
                    // "Paracetamol solución inyectable" y abajo "Administrar 1 g IV".
                    if (unidad == null && r + 1 < renglones.size() && SOLO_DOSIS.matcher(normalizar(renglones.get(r + 1))).matches()) {
                        unidad = dosisDespuesDe(normalizar(renglones.get(r + 1)), "", cantidad);
                    }
                    // Una solución escrita como renglón propio (lavado de vena): va con las soluciones.
                    if (SOLUCIONES.containsKey(c.farmaco()) && !enAlternativas) {
                        Matcher v = VOLUMEN.matcher(segmento);
                        acumularSolucion(soluciones, c.farmaco(), v.find() ? new BigDecimal(v.group(1).replace(',', '.')) : null,
                                original, porFarmaco.get(c.farmaco()));
                        continue;
                    }
                    Hallazgo nuevo = new Hallazgo(original, c.farmaco(), cantidad[0], unidad, porFarmaco.get(c.farmaco()),
                            enAlternativas, enAlternativas ? siNoHayDe : null);
                    // El mismo fármaco puede estar indicado y además como alternativa: se cuentan aparte.
                    String llave = c.farmaco() + (enAlternativas ? "#ALTERNATIVA" : "");
                    Integer previo = yaHallados.get(llave);
                    if (previo == null) {
                        yaHallados.put(llave, hallazgos.size());
                        hallazgos.add(nuevo);
                    } else if (hallazgos.get(previo).dosisUnidad() == null && unidad != null) {
                        hallazgos.set(previo, nuevo);
                    }
                }
            }
        }
        // Al final, las soluciones juntas por tipo y volumen, con cuántas bolsas se usan.
        hallazgos.addAll(soluciones.values());
        return hallazgos;
    }

    private static void acumularSolucion(Map<String, Hallazgo> soluciones, String farmaco, BigDecimal volumen, String texto,
                                         List<String> claves) {
        String llave = farmaco + "|" + (volumen == null ? "" : volumen.stripTrailingZeros().toPlainString());
        Hallazgo previo = soluciones.get(llave);
        int veces = previo == null ? 1 : previo.veces() + 1;
        soluciones.put(llave, new Hallazgo(previo == null ? texto : previo.texto(), farmaco, volumen, volumen == null ? null : "ml",
                claves, false, null, true, veces));
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

    /**
     * Cuando la concentración del catálogo no trae la dosis, se lee de la descripción:
     * "Cada pluma precargada contiene: Ixekizumab 80 mg Envase con…" → 80 mg por pieza.
     * Solo si lo que se describe es una pieza (pluma, ampolleta, frasco, tableta…): "Cada ml contiene",
     * "Cada 100 g contiene" o "Cada envase contiene" no dicen lo que trae una pieza y se ignoran.
     */
    static String contenidoDeDescripcion(String descripcion, BigDecimal[] cantidad) {
        if (descripcion == null) {
            return null;
        }
        // "100 000 UI" → "100000 UI" (miles separados con espacio).
        String texto = normalizar(descripcion).replaceAll("(\\d) (\\d{3})\\b", "$1$2").replaceAll("(\\d) (\\d{3})\\b", "$1$2");
        Matcher cada = CADA_CONTIENE.matcher(texto);
        if (!cada.find()) {
            return dosisUnicaAntesDelEnvase(texto, cantidad);
        }
        if (NO_ES_PIEZA.matcher(cada.group(1).trim()).matches()) {
            return null;
        }
        Matcher m = DOSIS.matcher(cada.group(2));
        if (!m.find() || m.group(3) != null) {
            return null;
        }
        cantidad[0] = new BigDecimal(m.group(1).replace(',', '.'));
        return unidad(m.group(2));
    }

    /**
     * Sin "Cada … contiene": "DENOSUMAB SOLUCION INYECTABLE 60 MG SUBCUTANEO ENVASE CON UNA JERINGA" → 60 mg.
     * Solo si antes de "ENVASE" hay UNA sola dosis y no es por volumen ("80 mg/ml", "8 mg/4 ml") ni por
     * superficie; si hay varias o ninguna, no se adivina.
     */
    private static String dosisUnicaAntesDelEnvase(String texto, BigDecimal[] cantidad) {
        int envase = texto.indexOf("ENVASE");
        String antes = envase < 0 ? texto : texto.substring(0, envase);
        Matcher m = DOSIS.matcher(antes);
        String unidad = null;
        BigDecimal valor = null;
        while (m.find()) {
            if (m.group(3) != null || antes.substring(m.end()).stripLeading().startsWith("/")) {
                return null;
            }
            String esta = unidad(m.group(2));
            if ("ml".equals(esta)) {
                return null;
            }
            BigDecimal numero = new BigDecimal(m.group(1).replace(',', '.'));
            if (valor != null && (valor.compareTo(numero) != 0 || !esta.equals(unidad))) {
                return null;
            }
            valor = numero;
            unidad = esta;
        }
        if (valor == null) {
            return null;
        }
        cantidad[0] = valor;
        return unidad;
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
