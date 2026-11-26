package com.example.backend.intelisis.service;

/**
 * Nombre del paciente siempre como "Nombre ApellidoPaterno ApellidoMaterno". En Intelisis, Cte.Nombre está
 * capturado a veces como "APELLIDOS NOMBRE" y a veces como "NOMBRE APELLIDOS" (el mismo paciente parecía
 * dos); las columnas Personal* lo traen separado. Si no están llenas, queda Cte.Nombre tal cual.
 * Es una expresión SQL sobre el alias {@code c} de Cte; solo para mostrar (las búsquedas siguen usando Cte.Nombre).
 */
public final class NombrePaciente {

    public static final String SQL = "CASE WHEN NULLIF(LTRIM(RTRIM(c.PersonalNombres)), '') IS NOT NULL "
            + "AND NULLIF(LTRIM(RTRIM(c.PersonalApellidoPaterno)), '') IS NOT NULL "
            + "THEN LTRIM(RTRIM(LTRIM(RTRIM(c.PersonalNombres)) + ' ' + LTRIM(RTRIM(c.PersonalApellidoPaterno)) + ' ' "
            + "+ ISNULL(LTRIM(RTRIM(c.PersonalApellidoMaterno)), ''))) ELSE LTRIM(RTRIM(c.Nombre)) END";

    private NombrePaciente() {
    }

    /**
     * Si Cte.Nombre viene como "PATERNO MATERNO NOMBRE(S)" (y no había columnas separadas), lo acomoda como
     * "NOMBRE(S) PATERNO MATERNO" usando el CURP: su letra 1 es del paterno, la 3 del materno y la 4 del nombre.
     * Si no se puede saber (menos de 3 palabras, sin CURP, o las dos lecturas cuadran) se deja como viene.
     */
    public static String ordenar(String nombre, String curp) {
        if (nombre == null || curp == null || curp.trim().length() < 4) {
            return nombre;
        }
        String[] p = nombre.trim().split("\\s+");
        if (p.length < 3) {
            return nombre;
        }
        String c = sinAcentos(curp.trim());
        char paterno = c.charAt(0), materno = c.charAt(2), deNombre = c.charAt(3);
        boolean apellidosPrimero = inicial(p[0]) == paterno && inicial(p[1]) == materno && inicial(p[2]) == deNombre;
        boolean nombrePrimero = inicial(p[p.length - 2]) == paterno && inicial(p[p.length - 1]) == materno && inicial(p[0]) == deNombre;
        if (!apellidosPrimero || nombrePrimero) {
            return nombre;
        }
        return String.join(" ", java.util.Arrays.copyOfRange(p, 2, p.length)) + " " + p[0] + " " + p[1];
    }

    /**
     * La misma persona escrita en cualquier orden ("GARCIA LOPEZ MARIA" o "María García López") da la misma llave:
     * sus palabras sin acentos, en mayúsculas y ordenadas.
     */
    public static String llave(String nombre) {
        return String.join(" ", palabras(nombre).stream().sorted().toList());
    }

    /** Palabras del nombre sin acentos y en mayúsculas (para buscar sin importar el orden). */
    public static java.util.List<String> palabras(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.asList(sinAcentos(nombre.trim()).split("\\s+"));
    }

    /**
     * ¿Es el paciente buscado, en cualquier orden? "fabian jimenez castillejos" encuentra "JIMENEZ CASTILLEJOS FABIAN" y también
     * "FABIAN JIMENEZ" o "JIMENEZ FABIAN" (guardado con menos palabras): o trae todo lo buscado, o todo lo suyo está en lo
     * buscado con al menos dos palabras en común. Un solo nombre ("FABIAN") no basta.
     */
    public static boolean contiene(String nombre, String buscado) {
        java.util.List<String> del = palabras(nombre);
        java.util.List<String> busca = palabras(buscado);
        if (busca.isEmpty() || del.isEmpty()) {
            return false;
        }
        boolean traeTodoLoBuscado = busca.stream().allMatch(p -> del.stream().anyMatch(d -> d.contains(p)));
        boolean todoLoSuyoEstaBuscado = del.size() >= 2 && del.stream().allMatch(busca::contains);
        return traeTodoLoBuscado || todoLoSuyoEstaBuscado;
    }

    /**
     * Pares de palabras para buscar en la base (cada par con LIKE): el paciente debe traer al menos un par, y luego se revisa
     * con contiene(). Con una sola palabra, el par es esa palabra dos veces.
     */
    public static java.util.List<String[]> pares(String buscado) {
        java.util.List<String> p = palabras(buscado).stream().distinct().toList();
        java.util.List<String[]> pares = new java.util.ArrayList<>();
        if (p.size() == 1) {
            pares.add(new String[]{p.get(0), p.get(0)});
        }
        for (int i = 0; i < p.size(); i++) {
            for (int j = i + 1; j < p.size(); j++) {
                pares.add(new String[]{p.get(i), p.get(j)});
            }
        }
        return pares;
    }

    /**
     * Condición SQL "trae algún par de las palabras" sobre la columna (sin acentos), para filtrar antes de contiene().
     * Agrega los parámetros en orden.
     */
    public static String algunPar(String columna, String buscado, java.util.List<Object> parametros) {
        String like = columna + " COLLATE Latin1_General_CI_AI LIKE ? COLLATE Latin1_General_CI_AI";
        java.util.List<String> condiciones = new java.util.ArrayList<>();
        for (String[] par : pares(buscado)) {
            parametros.add("%" + escaparLike(par[0]) + "%");
            parametros.add("%" + escaparLike(par[1]) + "%");
            condiciones.add("(" + like + " AND " + like + ")");
        }
        return condiciones.isEmpty() ? "1 = 0" : "(" + String.join(" OR ", condiciones) + ")";
    }

    private static String escaparLike(String palabra) {
        return palabra.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]");
    }

    private static char inicial(String palabra) {
        return sinAcentos(palabra).charAt(0);
    }

    private static String sinAcentos(String texto) {
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(java.util.Locale.ROOT);
    }
}
