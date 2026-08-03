package com.borgeiz.meutcc2026.auth

import com.borgeiz.meutcc2026.R
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val etName = findViewById<EditText>(R.id.etName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnCreate = findViewById<Button>(R.id.btnCreateAccount)

        btnCreate.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    val uid = auth.currentUser!!.uid
                    val profile = mapOf(
                        "name" to name,
                        "email" to email
                    )
                    db.child("users").child(uid).child("profile").setValue(profile)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Conta criada com sucesso", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.e("RegisterActivity", "Falha ao salvar perfil do usuário $uid", e)
                            Toast.makeText(
                                this,
                                "Conta criada, mas houve um erro ao salvar seu perfil. Verifique sua conexão e tente novamente mais tarde.",
                                Toast.LENGTH_LONG
                            ).show()
                            finish()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Erro: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
