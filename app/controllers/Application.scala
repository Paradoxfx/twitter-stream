package controllers

//import com.ning.http.client.oauth.{ConsumerKey, RequestToken}
import play.api.Play.current
import play.api.libs.iteratee.{Concurrent, Enumeratee, Enumerator, Iteratee}
import play.api.libs.json._
import play.api.libs.oauth.{ConsumerKey, OAuthCalculator, RequestToken}
import play.api.libs.ws.WS
import play.api.mvc._
import play.api.{Logger, _}
import play.extras.iteratees._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class Application extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index("Tweets"))
  }

  /*def tweets = WebSocket.acceptWithActor[String, JsValue] {
    request => out => TwitterStreamer.props(out)
  }*/

  /*def tweets = {

    val loggingIteratee = Iteratee.foreach[Array[Byte]] { array =>
      Logger.info(array.map(_.toChar).mkString)
    }

    credentials.map { case (consumerKey, requestToken) =>
      WS
        .url("https://stream.twitter.com/1.1/statuses/filter.json")
        .sign(OAuthCalculator(consumerKey, requestToken))
        .withQueryString("track" -> "reactive")
        .get { response =>

          Logger.info("Status: " + response.status)
          loggingIteratee
        }.map { _ =>
        Ok("Stream closed")
      }

    }
  }*/

  def tweets = Action.async {

    credentials.map { case (consumerKey, requestToken) =>

      val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]

      val jsonStream: Enumerator[JsObject] =
        enumerator &>
          Encoding.decode() &>
          Enumeratee.grouped(JsonIteratees.jsSimpleObject)

      val loggingIteratee = Iteratee.foreach[JsObject] { value =>
        Logger.info(value.toString)
      }

      jsonStream run loggingIteratee

      WS
        .url("https://stream.twitter.com/1.1/statuses/filter.json")
        .sign(OAuthCalculator(consumerKey, requestToken))
        .withQueryString("track" -> "brexit")
        .get { response =>
          Logger.info("Status: " + response.status)
          iteratee
        }.map { _ =>
        Ok("Stream closed")
      }
    } getOrElse {
      Future.successful {
        InternalServerError("Twitter credentials missing")
      }
    }
  }

  def credentials: Option[(ConsumerKey, RequestToken)] = for {
    apiKey <- Play.configuration.getString("twitter.apiKey")
    apiSecret <- Play.configuration.getString("twitter.apiSecret")
    token <- Play.configuration.getString("twitter.token")
    tokenSecret <- Play.configuration.getString("twitter.tokenSecret")
  } yield (
    ConsumerKey(apiKey, apiSecret),
    RequestToken(token, tokenSecret)
  )

}
