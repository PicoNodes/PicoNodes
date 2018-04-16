package picoide.server

import akka.actor.ActorSystem
import akka.stream.{TLSClientAuth, TLSProtocol}
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

object DownloaderTLSConfig {
  def sslContext: SSLContext = {
    // FIXME: Don't hard-code
    val password = "secret".toCharArray()

    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(
      getClass.getClassLoader.getResourceAsStream("picoserver-keystore.p12"),
      password)

    val trustMgrFactory = TrustManagerFactory.getInstance("SunX509")
    trustMgrFactory.init(keyStore)

    val keyMgrFactory = KeyManagerFactory.getInstance("SunX509")
    keyMgrFactory.init(keyStore, password)

    val ctx = SSLContext.getInstance("TLS")
    ctx.init(keyMgrFactory.getKeyManagers,
             trustMgrFactory.getTrustManagers,
             new SecureRandom)
    ctx
  }

  def negotiateNewSession(sslContext: SSLContext)(
      implicit actorSystem: ActorSystem): TLSProtocol.NegotiateNewSession = {
    val config = AkkaSSLConfig()

    val params = sslContext.getDefaultSSLParameters

    val protocols =
      config.configureProtocols(params.getProtocols, config.config)
    params.setProtocols(protocols)

    val cipherSuites =
      config.configureCipherSuites(params.getCipherSuites, config.config)
    params.setCipherSuites(cipherSuites)

    TLSProtocol.NegotiateNewSession
      .withCipherSuites(cipherSuites: _*)
      .withProtocols(protocols: _*)
      .withParameters(params)
      .withClientAuth(TLSClientAuth.None)
  }
}
