package com.app.pocketticket;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DB_NOMBRE = "pocketticket.db";
    public static final int DB_VERSION = 2;

    public static final String TABLA_GASTOS = "gastos";
    public static final String TABLA_INGRESOS = "ingresos";

    public static final String COL_ID = "id";
    public static final String COL_CONCEPTO = "concepto";
    public static final String COL_MONTO = "monto";
    public static final String COL_FECHA = "fecha";
    public static final String COL_IMAGEN_URI = "imagen_uri";

    private static final String CREAR_TABLA_GASTOS =
            "CREATE TABLE IF NOT EXISTS " + TABLA_GASTOS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_CONCEPTO + " TEXT, " +
                    COL_MONTO + " REAL, " +
                    COL_FECHA + " TEXT, " +
                    COL_IMAGEN_URI + " TEXT);";

    private static final String CREAR_TABLA_INGRESOS =
            "CREATE TABLE IF NOT EXISTS " + TABLA_INGRESOS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_MONTO + " REAL, " +
                    COL_FECHA + " TEXT);";

    private static volatile DatabaseHelper instancia;

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

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL(CREAR_TABLA_GASTOS);
        db.execSQL(CREAR_TABLA_INGRESOS);
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_gastos_fecha ON " +
                TABLA_GASTOS + "(" + COL_FECHA + ");");
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL(CREAR_TABLA_INGRESOS);
        }
    }

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

    public long insertarGasto(@NonNull Gasto gasto) {
        return insertarGasto(gasto.getConcepto(), gasto.getMonto(),
                gasto.getFecha(), gasto.getImagenUri());
    }

    public long insertarIngreso(double monto, @Nullable String fecha) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues valores = new ContentValues();
        valores.put(COL_MONTO, monto);
        valores.put(COL_FECHA, fecha);

        try {
            return db.insert(TABLA_INGRESOS, null, valores);
        } catch (Exception e) {
            return -1;
        }
    }

    @NonNull
    public ArrayList<Gasto> obtenerTodosLosGastos() {
        return consultarGastos(null, null);
    }

    @NonNull
    public ArrayList<Gasto> obtenerUltimosGastos(int limite) {
        if (limite <= 0) {
            return new ArrayList<>();
        }
        return consultarGastos(null, String.valueOf(limite));
    }

    @Nullable
    public Gasto obtenerGastoPorId(long id) {
        ArrayList<Gasto> lista = consultarGastos(COL_ID + " = ?",
                null, new String[]{String.valueOf(id)});
        return lista.isEmpty() ? null : lista.get(0);
    }

    public double obtenerTotalGastos() {
        return obtenerSuma(TABLA_GASTOS);
    }

    public double obtenerTotalIngresos() {
        return obtenerSuma(TABLA_INGRESOS);
    }

    private double obtenerSuma(String tabla) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT IFNULL(SUM(" + COL_MONTO + "), 0) FROM " + tabla;

        try (Cursor cursor = db.rawQuery(sql, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getDouble(0);
            }
        }
        return 0.0;
    }

    public int contarGastos() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLA_GASTOS, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

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

    public boolean eliminarGasto(int id) {
        return eliminarGasto((long) id);
    }

    public int eliminarTodosLosGastos() {
        SQLiteDatabase db = getWritableDatabase();
        try {
            return db.delete(TABLA_GASTOS, null, null);
        } catch (Exception e) {
            return 0;
        }
    }

    public int eliminarTodosLosIngresos() {
        SQLiteDatabase db = getWritableDatabase();
        try {
            return db.delete(TABLA_INGRESOS, null, null);
        } catch (Exception e) {
            return 0;
        }
    }

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