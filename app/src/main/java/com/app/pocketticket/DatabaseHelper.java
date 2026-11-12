package com.app.pocketticket;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * Archivo: app/src/main/java/com/app/pocketticket/DatabaseHelper.java
 *
 * Acceso a SQLite "clásico" (sin Room). Tabla única: gastos.
 *
 * Esquema:
 *   id         INTEGER PRIMARY KEY AUTOINCREMENT
 *   concepto   TEXT
 *   monto      REAL
 *   fecha      TEXT
 *   imagen_uri TEXT
 *
 * Nota de precisión: se usa REAL por requisito del proyecto. En producción
 * conviene guardar centavos en un INTEGER para evitar errores de redondeo.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    /* ------------------------- Constantes de la BD ------------------------- */

    public static final String DB_NOMBRE = "pocketticket.db";
    public static final int DB_VERSION = 1;

    public static final String TABLA_GASTOS = "gastos";

    public static final String COL_ID = "id";
    public static final String COL_CONCEPTO = "concepto";
    public static final String COL_MONTO = "monto";
    public static final String COL_FECHA = "fecha";
    public static final String COL_IMAGEN_URI = "imagen_uri";

    private static final String CREAR_TABLA_GASTOS =
            "CREATE TABLE " + TABLA_GASTOS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_CONCEPTO + " TEXT, " +
                    COL_MONTO + " REAL, " +
                    COL_FECHA + " TEXT, " +
                    COL_IMAGEN_URI + " TEXT" +
                    ");";

    /* ------------------------- Singleton (opcional) ----------------------- */

    private static volatile DatabaseHelper instancia;

    /**
     * Evita tener varias conexiones abiertas al mismo archivo .db.
     * Puedes instanciar `new DatabaseHelper(context)` sin problema.
     */
    public static DatabaseHelper getInstancia(Context context) {
        if (instancia == null) {
            synchronized (DatabaseHelper.class) {
                if (instancia == null) {
                    instancia = new DatabaseHelper(context.getApplicationContext());
                }
            }
        }
        return instancia;
    }

    public DatabaseHelper(Context context) {
        super(context, DB_NOMBRE, null, DB_VERSION);
    }

    /* ------------------------- Ciclo de vida SQLite ----------------------- */

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL(CREAR_TABLA_GASTOS);
        db.execSQL("CREATE INDEX idx_gastos_fecha ON " + TABLA_GASTOS + "(" + COL_FECHA + ");");
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // MVP: si cambia el esquema, se recrea la tabla.
        // (En producción aquí se migrarían los datos con ALTER TABLE.)
        db.execSQL("DROP TABLE IF EXISTS " + TABLA_GASTOS);
        onCreate(db);
    }

    @Override
    public void onConfigure(@NonNull SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    /* ------------------------------- CREATE ------------------------------- */

    /**
     * Inserta un gasto.
     *
     * @return el id autogenerado, o -1 si falló la inserción.
     */
    public long insertarGasto(@NonNull String concepto,
                              double monto,
                              @Nullable String fecha,
                              @Nullable String imagenUri) {

        SQLiteDatabase db = getWritableDatabase();
        ContentValues valores = new ContentValues();
        valores.put(COL_CONCEPTO, concepto);
        valores.put(COL_MONTO, monto);
        valores.put(COL_FECHA, fecha);

        if (imagenUri == null || imagenUri.trim().isEmpty()) {
            valores.putNull(COL_IMAGEN_URI);
        } else {
            valores.put(COL_IMAGEN_URI, imagenUri);
        }

        try {
            return db.insert(TABLA_GASTOS, null, valores);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Sobrecarga cómoda usando el modelo Gasto. */
    public long insertarGasto(@NonNull Gasto gasto) {
        return insertarGasto(gasto.getConcepto(), gasto.getMonto(),
                gasto.getFecha(), gasto.getImagenUri());
    }

    /* -------------------------------- READ -------------------------------- */

    /**
     * Devuelve TODOS los gastos, del más reciente al más antiguo.
     * Nunca devuelve null.
     */
    @NonNull
    public ArrayList<Gasto> obtenerTodosLosGastos() {
        return consultarGastos(null, null);
    }

    /**
     * Devuelve los últimos N gastos (para la lista de la pantalla principal).
     *
     * @param limite cantidad máxima de filas.
     */
    @NonNull
    public ArrayList<Gasto> obtenerUltimosGastos(int limite) {
        if (limite <= 0) {
            return new ArrayList<>();
        }
        return consultarGastos(null, String.valueOf(limite));
    }

    /** Busca un gasto por id. Devuelve null si no existe. */
    @Nullable
    public Gasto obtenerGastoPorId(long id) {
        ArrayList<Gasto> lista = consultarGastos(COL_ID + " = ?",
                null, new String[]{String.valueOf(id)});
        return lista.isEmpty() ? null : lista.get(0);
    }

    /**
     * Suma de todos los montos. Se usa para calcular el saldo disponible.
     * Con la tabla vacía devuelve 0.0.
     */
    public double obtenerTotalGastos() {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT IFNULL(SUM(" + COL_MONTO + "), 0) FROM " + TABLA_GASTOS;

        try (Cursor cursor = db.rawQuery(sql, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getDouble(0);
            }
        }
        return 0.0;
    }

    /** Cantidad de gastos registrados. */
    public int contarGastos() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLA_GASTOS, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    /* ------------------------------- DELETE ------------------------------- */

    /** @return true si se borró al menos una fila. */
    public boolean eliminarGasto(long id) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            int filas = db.delete(TABLA_GASTOS, COL_ID + " = ?",
                    new String[]{String.valueOf(id)});
            return filas > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** @return cantidad de filas eliminadas. */
    public int eliminarTodosLosGastos() {
        SQLiteDatabase db = getWritableDatabase();
        try {
            return db.delete(TABLA_GASTOS, null, null);
        } catch (Exception e) {
            return 0;
        }
    }

    /* ------------------------------ Internos ------------------------------ */

    /**
     * Consulta genérica con proyección completa y orden descendente por id.
     *
     * @param seleccion   cláusula WHERE (puede ser null).
     * @param limite      valor para LIMIT (puede ser null).
     * @param argumentos  argumentos de la cláusula WHERE.
     */
    @NonNull
    private ArrayList<Gasto> consultarGastos(@Nullable String seleccion,
                                             @Nullable String limite,
                                             @Nullable String... argumentos) {

        ArrayList<Gasto> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String[] columnas = {
                COL_ID, COL_CONCEPTO, COL_MONTO, COL_FECHA, COL_IMAGEN_URI
        };

        try (Cursor cursor = db.query(
                TABLA_GASTOS,
                columnas,
                seleccion,
                argumentos,
                null,
                null,
                COL_ID + " DESC",
                limite)) {

            while (cursor.moveToNext()) {
                lista.add(gastoDesdeCursor(cursor));
            }
        }

        return lista;
    }

    /** Convierte la fila actual del Cursor en un objeto Gasto. */
    @NonNull
    private Gasto gastoDesdeCursor(@NonNull Cursor cursor) {
        Gasto gasto = new Gasto();
        gasto.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)));
        gasto.setConcepto(cursor.getString(cursor.getColumnIndexOrThrow(COL_CONCEPTO)));
        gasto.setMonto(cursor.getDouble(cursor.getColumnIndexOrThrow(COL_MONTO)));
        gasto.setFecha(cursor.getString(cursor.getColumnIndexOrThrow(COL_FECHA)));
        gasto.setImagenUri(cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGEN_URI)));
        return gasto;
    }
}
