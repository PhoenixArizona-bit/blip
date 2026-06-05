package com.blip.app.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlipCrypto @Inject constructor() {

    companion object {
        private const val KEY_STORE = "AndroidKeyStore"
        private const val BLIP_KEY_ALIAS = "blip_identity_key"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val EC_CURVE = "secp256r1"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEY_STORE).apply { load(null) }

    // ─── Identity Key Pair (persisted in AndroidKeyStore) ─────────────────────

    fun getOrCreateIdentityKeyPair(): KeyPair {
        if (!keyStore.containsAlias(BLIP_KEY_ALIAS)) {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, KEY_STORE
            )
            keyPairGenerator.initialize(
                KeyGenParameterSpec.Builder(
                    BLIP_KEY_ALIAS,
                    KeyProperties.PURPOSE_AGREE_KEY or KeyProperties.PURPOSE_SIGN
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
            keyPairGenerator.generateKeyPair()
        }
        val privateKey = keyStore.getKey(BLIP_KEY_ALIAS, null) as PrivateKey
        val publicKey = keyStore.getCertificate(BLIP_KEY_ALIAS).publicKey
        return KeyPair(publicKey, privateKey)
    }

    fun getPublicKeyBytes(): ByteArray {
        return getOrCreateIdentityKeyPair().public.encoded
    }

    // ─── ECDH Shared Secret ───────────────────────────────────────────────────

    fun deriveSharedSecret(theirPublicKeyBytes: ByteArray): SecretKey {
        val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
        val theirPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(theirPublicKeyBytes))
        val keyAgreement = KeyAgreement.getInstance("ECDH", KEY_STORE)
        keyAgreement.init(getOrCreateIdentityKeyPair().private)
        keyAgreement.doPhase(theirPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()
        // Derive 256-bit AES key via SHA-256
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(sharedSecret)
        return SecretKeySpec(keyBytes, "AES")
    }

    // ─── AES-GCM Encrypt ──────────────────────────────────────────────────────

    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext)
        // Prepend IV to ciphertext
        return iv + ciphertext
    }

    fun encryptString(plaintext: String, key: SecretKey): ByteArray =
        encrypt(plaintext.toByteArray(Charsets.UTF_8), key)

    // ─── AES-GCM Decrypt ──────────────────────────────────────────────────────

    fun decrypt(ciphertextWithIv: ByteArray, key: SecretKey): ByteArray {
        val iv = ciphertextWithIv.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = ciphertextWithIv.copyOfRange(GCM_IV_LENGTH, ciphertextWithIv.size)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    fun decryptToString(ciphertextWithIv: ByteArray, key: SecretKey): String =
        decrypt(ciphertextWithIv, key).toString(Charsets.UTF_8)

    // ─── Session Key Cache ────────────────────────────────────────────────────
    // In production, use Double Ratchet. This is simplified ECDH per session.

    private val sessionKeys = mutableMapOf<String, SecretKey>()

    fun getOrDeriveSessionKey(peerId: String, theirPublicKeyBytes: ByteArray): SecretKey {
        return sessionKeys.getOrPut(peerId) { deriveSharedSecret(theirPublicKeyBytes) }
    }

    fun clearSessionKey(peerId: String) = sessionKeys.remove(peerId)

    // ─── Signing (optional integrity) ─────────────────────────────────────────

    fun sign(data: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA", KEY_STORE)
        signature.initSign(getOrCreateIdentityKeyPair().private)
        signature.update(data)
        return signature.sign()
    }

    fun verify(data: ByteArray, sig: ByteArray, publicKeyBytes: ByteArray): Boolean {
        return try {
            val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(publicKey)
            signature.update(data)
            signature.verify(sig)
        } catch (e: Exception) {
            false
        }
    }
}
