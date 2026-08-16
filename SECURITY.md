# Security Policy Argos

Argos è un client **non ufficiale** per le API non documentate di Argo ScuolaNext. Visto che fa reverse-engineering di un protocollo che non controlliamo, tratta ogni release come potenzialmente fragile e ogni segnalazione di vulnerabilità come confidenziale.

## Versioni supportate

Solo l'ultimo tag CalVer (`v2026.08.x` al momento della scrittura) riceve correzioni di sicurezza. Le patch alzano solo la componente `MICRO` della versione.

## Segnalare una vulnerabilità

**Non aprire un issue pubblico per nulla di sensibile.**

1. **Canale preferito.** GitHub, scheda *Security*, voce **Report a vulnerability** (security advisory privato su https://github.com/Hydr46605/Argos).
2. **Canale alternativo.** Email a `bryamjamb@proton.me` con oggetto `[argos-security] <breve descrizione>`. Usa la cifratura se puoi.

Includi versione/tag affetto, una riproduzione minima, una valutazione dell'impatto e, se possibile, una correzione suggerita. Non includere credenziali reali di Argo né dati personali (nomi degli studenti, voti, email) nei report, redatti meglio.

## Tempi di risposta

- **48 ore.** Conferma di ricezione.
- **7 giorni.** Verdetto di triage (accettato / rifiutato con motivazione).
- **30 giorni.** Fix rilasciato con una voce di changelog sotto `Security`, salvo un tempo diverso concordato quando l'API upstream va prima ri-auditata.

## Note di scope

- Le credenziali/token trapelate perché *tu* hai eseguito esempi contro account reali sono fuori scope. Le trapelate attraverso *il comportamento della libreria* (log, messaggi di eccezione, testo salvato in chiaro) sono sempre in scope.
- La libreria non logga mai intenzionalmente i valori di `access_token`, `refresh_token`, `x-auth-token`, le password o i payload dei cookie. Qualsiasi deviazione da quella garanzia è un finding a severità critica.