package com.garfiec.librechat.feature.chat.components

/**
 * A web-search reply as a v0.8.8 server persisted it (claude-haiku-4-5 through the `web_search`
 * tool, Keenable search), trimmed to six results per search and with its ids replaced. The model
 * wrote every marker as the literal escape (`\ue202`), with three groups and nine highlights.
 */
internal val WebSearchReplyFixture = """
{
  "messageId": "fixture-response",
  "conversationId": "fixture-conversation",
  "parentMessageId": "fixture-request",
  "sender": "Search Tester",
  "isCreatedByUser": false,
  "text": "",
  "content": [
    {
      "type": "text",
      "text": "Based on the search results, I should clarify that Apple's \"Pro and Tell\" event is scheduled for **October 28, 2026**, which is still upcoming (3 days from the conversation date of October 25). However, I can provide you with the expected announcements based on official confirmations and reporting:\n\n## Main Expected Announcements at Apple's \"Pro and Tell\" Event (October 28, 2026)\n\n### Event Format & Location\n\\ue203Apple will hold a special \"Pro and tell\" event on October 28 in Los Angeles\\ue204 \\ue200\\ue202turn0search0\\ue202turn1search3\\ue201, with invitations sent to media and content creators. \\ue203It won't be a traditional live-streamed Apple event, but rather a briefing for the press and creators\\ue204\\ue202turn1search2, with product announcements coming via press releases.\n\n### MacBook Pro with Touchscreen Display\n\\ue203Apple will debut its first touchscreen MacBook Pro with an OLED display\\ue204 \\ue200\\ue202turn0search0\\ue202turn1search3\\ue201. This represents a significant shift, as \\ue203the incorporation of a touch panel in a laptop with macOS marks a departure from a stance the company maintained for over two decades\\ue204\\ue202turn0search0. \\ue203The redesign will affect both 14 and 16-inch MacBook Pro variants\\ue204\\ue202turn0search0, and these models will include OLED displays and Dynamic Island.\n\n### Additional Mac Updates\n\\ue203Apple will introduce a new entry-level MacBook Pro with an M6 chip\\ue204\\ue202turn1search3 and \\ue203a refreshed iMac\\ue204\\ue202turn1search3, also featuring M6 processors.\n\n### iPad Mini with OLED Screen\n\\ue203An iPad mini with a redesigned OLED display\\ue204 \\ue200\\ue202turn0search2\\ue202turn1search3\\ue201 is expected to be announced.\n\n### Release Timeline\n\\ue203The devices are expected to be released on Friday, November 6\\ue204\\ue202turn1search4, following the October 28 announcement."
    }
  ],
  "attachments": [
    {
      "type": "web_search",
      "toolCallId": "call_fixture_0",
      "messageId": "fixture-response",
      "conversationId": "fixture-conversation",
      "web_search": {
        "turn": 0,
        "organic": [
          {
            "title": "Apple convoca al evento “Pro and Tell”: el primer MacBook Pro con pantalla OLED táctil e iPad mini 8 asoman para el 28 de octubre",
            "link": "https://fayerwayer.com/moviles/2026/10/24/apple-convoca-al-evento-pro-and-tell-el-primer-macbook-pro-con-pantalla-oled-tactil-e-ipad-mini-8-asoman-para-el-28-de-octubre",
            "snippet": "La firma de la manzana mordida alista los preparativos para una de sus presentaciones de hardware má",
            "date": "2026-10-24T11:15:15Z",
            "attribution": "Apple convoca al evento “Pro and Tell”: el primer MacBook Pro con pantalla OLED táctil e iPad mini 8 asoman para el 28 de octubre"
          },
          {
            "title": "Apple presentaría su primera MacBook con pantalla táctil: esto es lo que se sabe del evento del 28 de octubre",
            "link": "https://es-us.noticias.yahoo.com/apple-presentar%C3%ADa-macbook-pantalla-t%C3%A1ctil-205146631.html",
            "snippet": "El miércoles 28 de octubre, Apple reunirá a periodistas y creadores de contenido en Los Ángeles para",
            "date": "2026-10-21T20:51:46Z",
            "attribution": "Apple presentaría su primera MacBook con pantalla táctil: esto es lo que se sabe del evento del 28 de octubre"
          },
          {
            "title": "Evento hardware Apple Pro and tell: rumor e data di lancio",
            "link": "https://hypebeast.com/it/2026/10/apple-announces-october-28-pro-and-tell-event",
            "snippet": "Apple terrà il suo evento hardware speciale \"Pro and tell\" a Los Angeles il 28 ottobre Apple si prep",
            "date": "2026-10-20T08:57:14Z",
            "attribution": "Evento hardware Apple Pro and tell: rumor e data di lancio"
          },
          {
            "title": "Apple fissa un evento il 28 ottobre mentre si avvicinano i MacBook touchscreen",
            "link": "https://it.techbriefly.com/apple-fissa-un-evento-il-28-ottobre-mentre-si-avvicinano-i-macbook-touchscreen-tech-101673",
            "snippet": "Apple ha confermato il suo terzo evento di prodotto della stagione per il 28 ottobre, invitando stam",
            "date": "2026-10-20T09:34:20Z",
            "attribution": "Apple fissa un evento il 28 ottobre mentre si avvicinano i MacBook touchscreen"
          },
          {
            "title": "Apple October 28 invitations go live: All about 'Pro and tell' hands-on experience",
            "link": "https://me.mashable.com/tech/77397/apple-october-28-invitations-go-live-all-about-pro-and-tell-hands-on-experience",
            "snippet": "Following its [\"Welcome Home\"](/tech/77003/apple-welcome-home-event-on-october-13-new-launches-expec",
            "date": "2026-10-20T11:32:55Z",
            "attribution": "Apple October 28 invitations go live: All about 'Pro and tell' hands-on experience"
          },
          {
            "title": "Apple celebrará un nuevo evento de presentación sobre sus ordenadores portátiles MacBook Pro el próximo 28 de octubre",
            "link": "https://prensamercosur.org/2026/10/20/apple-celebrara-un-nuevo-evento-de-presentacion-sobre-sus-ordenadores-portatiles-macbook-pro-el-proximo-28-de-octubre",
            "snippet": "****Apple** ha anunciado un nuevo evento de presentación de producto, que se celebrará el **28** de ",
            "date": "2026-10-20T14:06:40Z"
          }
        ],
        "topStories": []
      }
    },
    {
      "type": "web_search",
      "toolCallId": "call_fixture_1",
      "messageId": "fixture-response",
      "conversationId": "fixture-conversation",
      "web_search": {
        "turn": 1,
        "organic": [
          {
            "title": "Apple announces product launch event for Oct. 28. Analysts expect touchscreen laptop",
            "link": "https://cnbc.com/2026/10/19/apple-product-launch-event-oct-28-analysts-expect-touchscreen-laptop.html",
            "snippet": "Tech Apple announces product launch event for Oct. 28. Analysts expect touchscreen laptop Key Points",
            "date": "2026-10-19T17:24:49Z",
            "attribution": "Apple announces product launch event for Oct. 28. Analysts expect touchscreen laptop"
          },
          {
            "title": "Apple's October 28 'Pro and Tell' Launch: What's Coming",
            "link": "https://macrumors.com/2026/10/19/apple-october-28-product-launch",
            "snippet": "Apple’s October 28 ‘Pro and Tell’ Launch: What’s Coming Apple will announce a second wave of new pro",
            "date": "2026-10-19T23:51:00Z",
            "attribution": "Apple's October 28 'Pro and Tell' Launch: What's Coming"
          },
          {
            "title": "What To Expect At Apple's 'Pro And Tell' Event On October 28",
            "link": "https://engadget.com/2292117/apple-pro-and-tell-event-october-28-what-to-expect",
            "snippet": "What to expect at Apple’s ‘Pro and Tell’ event on October 28 From the first touchscreen MacBook to a",
            "date": "2026-10-20T22:00:00Z",
            "attribution": "What To Expect At Apple's 'Pro And Tell' Event On October 28"
          },
          {
            "title": "Apple Announces Oct. 28 Event for Touch-Screen MacBook Pro",
            "link": "https://bloomberg.com/news/articles/2026-10-19/apple-announces-oct-28-event-for-touch-screen-macbook-pro",
            "snippet": "Apple Announces Oct. 28 Event for Touch-Screen MacBook Pro Apple Inc. on Monday confirmed that it’s ",
            "date": "2026-10-19T16:10:12Z",
            "attribution": "Apple Announces Oct. 28 Event for Touch-Screen MacBook Pro"
          },
          {
            "title": "Apple Expected to Release New Products on Next Four Fridays",
            "link": "https://macrumors.com/2026/10/18/apple-product-releases-next-four-fridays",
            "snippet": "Apple likely plans to release MacBook Pro models with OLED displays, 14-inch MacBook Pro and iMac mo",
            "date": "2026-10-18T18:01:43Z",
            "attribution": "Apple Expected to Release New Products on Next Four Fridays"
          },
          {
            "title": "Apple presentará su primer MacBook Pro con pantalla táctil el 28 de octubre",
            "link": "https://finance.yahoo.com/noticias/apple-presentar%C3%A1-macbook-pro-pantalla-172007822.html",
            "snippet": "(Bloomberg) -- Apple Inc. confirmó este lunes que celebrará un evento de lanzamiento de producto el ",
            "date": "2026-10-19T17:20:07Z"
          }
        ],
        "topStories": []
      }
    }
  ]
}
""".trimIndent()
