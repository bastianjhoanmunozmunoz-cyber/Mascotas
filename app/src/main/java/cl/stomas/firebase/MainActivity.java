package cl.stomas.firebase;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private EditText nombre, especie, raza, edad;
    private ListView lista;
    private MascotaAdapter adapter;
    private ListenerRegistration listenerMascotas;
    private final List<MascotaGrupo> mascotas = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        nombre = findViewById(R.id.nombre);
        especie = findViewById(R.id.especie);
        raza = findViewById(R.id.raza);
        edad = findViewById(R.id.edad);
        lista = findViewById(R.id.lista);

        adapter = new MascotaAdapter();
        lista.setAdapter(adapter);
        lista.setEmptyView(findViewById(R.id.listaVacia));
        findViewById(R.id.guardar).setOnClickListener(v -> guardar());
        findViewById(R.id.cargar).setOnClickListener(v -> cargar());

        if (auth.getCurrentUser() != null) {
            cargar();
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener(result -> cargar())
                .addOnFailureListener(error -> aviso("No se pudo conectar con Firebase"));
        }
    }

    private void guardar() {
        if (auth.getCurrentUser() == null) {
            aviso("Conectando con Firebase, intenta de nuevo");
            return;
        }

        String n = nombre.getText().toString().trim();
        String e = especie.getText().toString().trim();
        String r = raza.getText().toString().trim();
        String a = edad.getText().toString().trim();

        if (n.isEmpty() || e.isEmpty() || a.isEmpty()) {
            aviso("Completa nombre, especie y edad");
            return;
        }
        final String edadNormalizada;
        try {
            int edadEnAnios = Integer.parseInt(a);
            if (edadEnAnios < 0) {
                aviso("La edad no puede ser negativa");
                return;
            }
            edadNormalizada = Integer.toString(edadEnAnios);
        } catch (NumberFormatException error) {
            aviso("Ingresa la edad en años como número entero");
            return;
        }

        String ownerId = auth.getCurrentUser().getUid();
        db.collection("mascotas").whereEqualTo("ownerId", ownerId).get()
            .addOnSuccessListener(snapshot -> {
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    if (esDuplicado(doc, n, e, r, edadNormalizada)) {
                        aviso("Ese registro ya existe");
                        return;
                    }
                }
                guardarRegistro(ownerId, n, e, r, edadNormalizada);
            })
            .addOnFailureListener(error -> aviso("No se pudo revisar la lista. Intenta de nuevo"));
    }

    private boolean esDuplicado(DocumentSnapshot doc, String n, String e, String r, String a) {
        return n.equals(valor(doc.getString("nombre")))
            && e.equals(valor(doc.getString("especie")))
            && r.equals(valor(doc.getString("raza")))
            && a.equals(valor(doc.getString("edad")));
    }

    private void guardarRegistro(String ownerId, String n, String e, String r, String a) {
        Map<String, Object> dato = new HashMap<>();
        dato.put("nombre", n);
        dato.put("especie", e);
        dato.put("raza", r);
        dato.put("edad", a);
        dato.put("ownerId", ownerId);
        dato.put("creadoEn", FieldValue.serverTimestamp());

        String clave = ownerId + "\u0000" + n + "\u0000" + e + "\u0000" + r + "\u0000" + a;
        String id = idDeterminista(clave);
        db.collection("mascotas").document(id).set(dato)
            .addOnSuccessListener(result -> {
                aviso("Mascota guardada");
                nombre.setText("");
                especie.setText("");
                raza.setText("");
                edad.setText("");
            })
            .addOnFailureListener(error -> aviso("No se pudo guardar la mascota"));
    }

    private String idDeterminista(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder id = new StringBuilder();
            for (byte valor : hash) {
                int numero = valor & 0xff;
                id.append(Character.forDigit(numero >>> 4, 16));
                id.append(Character.forDigit(numero & 0x0f, 16));
            }
            return id.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("No está disponible SHA-256", error);
        }
    }

    private void cargar() {
        if (auth.getCurrentUser() == null) return;
        if (listenerMascotas != null) listenerMascotas.remove();

        String ownerId = auth.getCurrentUser().getUid();
        listenerMascotas = db.collection("mascotas")
            .whereEqualTo("ownerId", ownerId)
            .addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    aviso("No se pudo actualizar la lista");
                    return;
                }
                if (snapshot == null) return;

                Map<String, MascotaGrupo> grupos = new LinkedHashMap<>();
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    MascotaGrupo grupo = new MascotaGrupo(doc);
                    String clave = claveDe(grupo);
                    if (!grupos.containsKey(clave)) grupos.put(clave, grupo);
                    grupos.get(clave).copias.add(doc);
                }
                mascotas.clear();
                mascotas.addAll(grupos.values());
                adapter.notifyDataSetChanged();
            });
    }

    private String claveDe(MascotaGrupo grupo) {
        return segmento(grupo.nombre) + segmento(grupo.especie)
            + segmento(grupo.raza) + segmento(grupo.edad);
    }

    private String segmento(String texto) {
        return texto.length() + ":" + texto;
    }

    private void confirmarEliminacion(MascotaGrupo grupo) {
        String mensaje;
        if (grupo.copias.size() > 1) {
            mensaje = "Hay " + grupo.copias.size() + " registros idénticos. Se eliminarán todos.";
        } else {
            mensaje = "¿Quieres eliminar el registro de " + grupo.nombre + "?";
        }

        new AlertDialog.Builder(this)
            .setTitle("Eliminar registro")
            .setMessage(mensaje)
            .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
            .setPositiveButton("Eliminar", (dialog, which) -> eliminarGrupo(grupo))
            .show();
    }

    private void eliminarGrupo(MascotaGrupo grupo) {
        WriteBatch batch = db.batch();
        for (DocumentSnapshot copia : grupo.copias) {
            batch.delete(copia.getReference());
        }
        batch.commit()
            .addOnSuccessListener(result -> aviso(grupo.copias.size() > 1
                ? "Registros repetidos eliminados" : "Registro eliminado"))
            .addOnFailureListener(error -> aviso("No se pudo eliminar el registro"));
    }

    private String textoMascota(MascotaGrupo grupo) {
        String texto = valor(grupo.nombre) + " · " + valor(grupo.especie) + " · "
            + (grupo.raza.isEmpty() ? "Sin raza" : grupo.raza)
            + " · " + valor(grupo.edad) + " años";
        if (grupo.copias.size() > 1) texto += " (" + grupo.copias.size() + " copias)";
        return texto;
    }

    private String valor(String texto) {
        return texto == null ? "" : texto;
    }

    private void aviso(String texto) {
        Toast.makeText(this, texto, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        if (listenerMascotas != null) listenerMascotas.remove();
        super.onDestroy();
    }

    private class MascotaGrupo {
        final String nombre;
        final String especie;
        final String raza;
        final String edad;
        final List<DocumentSnapshot> copias = new ArrayList<>();

        MascotaGrupo(DocumentSnapshot doc) {
            nombre = valor(doc.getString("nombre"));
            especie = valor(doc.getString("especie"));
            raza = valor(doc.getString("raza"));
            edad = valor(doc.getString("edad"));
        }
    }

    private class MascotaAdapter extends ArrayAdapter<MascotaGrupo> {
        MascotaAdapter() {
            super(MainActivity.this, 0, mascotas);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View fila = convertView;
            if (fila == null) {
                fila = LayoutInflater.from(MainActivity.this)
                    .inflate(R.layout.item_mascota, parent, false);
            }
            MascotaGrupo grupo = getItem(position);
            TextView detalle = fila.findViewById(R.id.detalleMascota);
            Button eliminar = fila.findViewById(R.id.eliminarMascota);
            detalle.setText(textoMascota(grupo));
            eliminar.setOnClickListener(v -> confirmarEliminacion(grupo));
            return fila;
        }
    }
}


