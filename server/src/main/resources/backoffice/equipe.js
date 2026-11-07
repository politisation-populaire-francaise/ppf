// Politisation Populaire Française : cérémonie des clés d'accès de l'espace de l'équipe.
// The only script of the back-office (contract, section 3): the WebAuthn API exists only in JavaScript.
// It reads what to do from the data attributes of the button, posts the answer of the key to the server
// and writes the server's French messages with textContent. No inline script, no third party, no storage.
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

  function postJson(url, body, csrf) {
    var headers = { "Content-Type": "application/json" };
    if (csrf) headers["X-PPF-CSRF"] = csrf;
    return fetch(url, {
      method: "POST",
      headers: headers,
      body: JSON.stringify(body),
      credentials: "same-origin",
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

  function creationOptions(answer) {
    var options = answer.publicKey;
    options.challenge = toBytes(options.challenge);
    options.user.id = toBytes(options.user.id);
    options.excludeCredentials = (options.excludeCredentials || []).map(function (c) {
      return { type: c.type, id: toBytes(c.id) };
    });
    return options;
  }

  function requestOptions(answer) {
    var options = answer.publicKey;
    options.challenge = toBytes(options.challenge);
    options.allowCredentials = (options.allowCredentials || []).map(function (c) {
      return { type: c.type, id: toBytes(c.id) };
    });
    return options;
  }

  function registrationBody(ceremony, credential, extra) {
    var body = {
      ceremony: ceremony,
      credential_id: toBase64Url(credential.rawId),
      client_data: toBase64Url(credential.response.clientDataJSON),
      attestation_object: toBase64Url(credential.response.attestationObject)
    };
    return Object.assign(body, extra);
  }

  function assertionBody(ceremony, credential, extra) {
    var response = credential.response;
    var body = {
      ceremony: ceremony,
      credential_id: toBase64Url(credential.rawId),
      client_data: toBase64Url(response.clientDataJSON),
      authenticator_data: toBase64Url(response.authenticatorData),
      signature: toBase64Url(response.signature)
    };
    if (response.userHandle && response.userHandle.byteLength > 0) body.user_handle = toBase64Url(response.userHandle);
    return Object.assign(body, extra);
  }

  function nameOf(button) {
    var field = button.dataset.nameField ? document.getElementById(button.dataset.nameField) : null;
    var value = field ? field.value.trim() : "";
    return value ? { name: value } : {};
  }

  function go(path) {
    // Only paths of the back-office itself.
    if (typeof path === "string" && path.charAt(0) === "/" && path.charAt(1) !== "/") window.location.assign(path);
  }

  var ceremonies = {
    signin: function () {
      return postJson("/webauthn/connexion/options", {}).then(function (answer) {
        return navigator.credentials.get({ publicKey: requestOptions(answer) }).then(function (credential) {
          return postJson("/webauthn/connexion", assertionBody(answer.ceremony, credential, {}));
        });
      }).then(function (done) { go(done.redirect); });
    },
    enrol: function (button) {
      return postJson("/webauthn/inscription/options", { invitation: button.dataset.invitation }).then(function (answer) {
        return navigator.credentials.create({ publicKey: creationOptions(answer) }).then(function (credential) {
          return postJson("/webauthn/inscription", registrationBody(answer.ceremony, credential, nameOf(button)));
        });
      }).then(function (done) { go(done.redirect); });
    },
    "add-key": function (button) {
      var csrf = button.dataset.csrf;
      return postJson("/webauthn/cle/options", {}, csrf).then(function (answer) {
        return navigator.credentials.create({ publicKey: creationOptions(answer) }).then(function (credential) {
          return postJson("/webauthn/cle", registrationBody(answer.ceremony, credential, Object.assign({ csrf: csrf }, nameOf(button))), csrf);
        });
      }).then(function (done) { go(done.redirect); });
    },
    confirm: function (button) {
      var csrf = button.dataset.csrf;
      return postJson("/webauthn/confirmation/options", {}, csrf).then(function (answer) {
        return navigator.credentials.get({ publicKey: requestOptions(answer) }).then(function (credential) {
          return postJson("/webauthn/confirmation", assertionBody(answer.ceremony, credential, { csrf: csrf }), csrf);
        });
      }).then(function () {
        var form = document.getElementById(button.dataset.form);
        if (form) form.submit();
      });
    }
  };

  function prepare(button) {
    var status = document.getElementById(button.dataset.status);
    function say(text) {
      if (status && text) status.textContent = text;
    }
    var run = ceremonies[button.dataset.ceremony];
    if (!run) return;
    if (!window.PublicKeyCredential || !navigator.credentials) {
      button.disabled = true;
      say(button.dataset.msgUnsupported);
      return;
    }
    button.addEventListener("click", function () {
      button.disabled = true;
      say(button.dataset.msgWorking);
      run(button).catch(function (error) {
        say((error && error.serverMessage) || button.dataset.msgFailed);
      }).then(function () {
        button.disabled = false;
      });
    });
  }

  function start() {
    var buttons = document.querySelectorAll("button[data-ceremony]");
    for (var i = 0; i < buttons.length; i++) prepare(buttons[i]);
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start);
  else start();
})();
