// Politisation Populaire Française : approbation d'une signature de la plateforme par clé d'accès.
// The only script of the approval page, served by the signing service itself on its own origin (contract
// docs/integration/service-signature.md, version 1.2): the WebAuthn API exists only in JavaScript. It asks
// the service for the options, lets the browser sign them with the member's passkey, posts the answer back
// to the service, and writes the service's French messages with textContent. No inline script, no third
// party, no storage, no cookie.
"use strict";

(function () {
  function toBytes(value) {
    var base64 = value.replace(/-/g, "+").replace(/_/g, "/");
    while (base64.length % 4 !== 0) base64 += "=";
    var binary = atob(base64);
    var bytes = new Uint8Array(binary.length);
    for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes.buffer;
  }

  function toBase64Url(buffer) {
    var bytes = new Uint8Array(buffer);
    var binary = "";
    for (var i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }

  function postJson(url, body) {
    return fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      credentials: "omit",
      cache: "no-store",
      redirect: "error"
    }).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (data) {
        if (!response.ok) {
          var error = new Error("refused");
          error.serverMessage = data && data.message;
          throw error;
        }
        return data;
      });
    });
  }

  function requestOptions(answer) {
    var options = answer.publicKey;
    options.challenge = toBytes(options.challenge);
    options.allowCredentials = (options.allowCredentials || []).map(function (c) {
      return { type: c.type, id: toBytes(c.id) };
    });
    return options;
  }

  function assertionBody(credential) {
    var response = credential.response;
    var body = {
      credential_id: toBase64Url(credential.rawId),
      client_data: toBase64Url(response.clientDataJSON),
      authenticator_data: toBase64Url(response.authenticatorData),
      signature: toBase64Url(response.signature)
    };
    if (response.userHandle && response.userHandle.byteLength > 0) body.user_handle = toBase64Url(response.userHandle);
    return body;
  }

  function prepare(button) {
    var status = document.getElementById(button.dataset.status);
    function say(text) {
      if (status && text) status.textContent = text;
    }
    if (!window.PublicKeyCredential || !navigator.credentials) {
      button.disabled = true;
      say(button.dataset.msgUnsupported);
      return;
    }
    var base = "/approbation/" + encodeURIComponent(button.dataset.use) + "/" + encodeURIComponent(button.dataset.event);
    button.addEventListener("click", function () {
      button.disabled = true;
      say(button.dataset.msgWorking);
      postJson(base + "/options", {}).then(function (answer) {
        return navigator.credentials.get({ publicKey: requestOptions(answer) });
      }).then(function (credential) {
        return postJson(base, assertionBody(credential));
      }).then(function (done) {
        say(done.message || button.dataset.msgDone);
        window.setTimeout(function () { window.location.reload(); }, 1500);
      }).catch(function (error) {
        button.disabled = false;
        say((error && error.serverMessage) || button.dataset.msgFailed);
      });
    });
  }

  function start() {
    var button = document.getElementById("approuver");
    if (button) prepare(button);
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start);
  else start();
})();
