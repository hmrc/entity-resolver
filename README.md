entity-resolver
=============

## IMPORTANT NOTE
Proxying calls to preferences is now deprecated. 
Use the direct API available on the `preferences` service instead.

## Run the tests and sbt fmt before raising a PR

Format:

`sbt fmt`

Then run the tests and coverage report:

`sbt clean coverage test coverageReport`

If your build fails due to poor test coverage, *DO NOT* lower the test coverage threshold, instead inspect the generated report located here on your local repo: `/target/scala-2.12/scoverage-report/index.html`

Then run the integration tests:
`sm2 --start DC_ENTITY_RESOLVER_IT`
`sbt it/test`
`sm2 --stop DC_ENTITY_RESOLVER_IT`

The entity-resolver is a microservice owned by the Digital Contact (DC) team. It has been created as a result of DC's move towards [Tax Identifier agnostic preferences](https://jira.tools.tax.service.gov.uk/browse/DC-191). The entity-resolver has been introduced to decouple the [preferences microservice](https://github.tools.tax.service.gov.uk/HMRC/preferences) from being tied to using SaUtrs (Self Assessment Unique Tax References) or Ninos (National Insurance Numbers)

Entity-resolver's purpose is to allow the introduction of **Entity Identifiers (entity Ids)** into the preferences microservice. The Preferences micro service no longer uses SaUtr or Nino.
This will result in the entity-resolver being the holder of all SaUtrs, Ninos and other tax identifiers and the mapping between them and the associated preference.

The entity-resolver is effectively a proxy for the preference service. Preferences existing clients have been altered to access the preference service via the entity-resolver service.
 
### Configuration of entity-resolver

The **application.conf** configuration file is used to map incoming requests to the appropriate requests for the new Entity Id based preferences service.  

The **pathMapping** configuration file key and its associated data will be used to demonstrate how this works.

For instance, if the pathMapping entry had the configuration:

```
pathMapping = [
    {
        taxIdExtraction {
            regex = "preferences/sa/individual/([a-z|0-9|-]*)/print-suppression"
            idType = "sautr"
        }
        serviceName = "preferences"
        destinationUrlPattern = "/preferences/%s"
    }
]
```

then, when the entity-resolver receives a request of the form:

```
GET /preferences/sa/individual/:utr/print-suppression
```

the entity-resolver will match that request using the **regex** in the **taxIdExtraction**.

Once the request has been matched, the entity-resolver will then look-up the extracted **sautr** in its entity collection.

_If_ the **sautr** is **found**, then the matching Entity Id is read and then used in the next stage of the mapping.

The **serviceName** tells entity-resolver the name of the service to map the request to and the **destinationUrlPattern** is used to build the request to send to that service.

In the above example configuration the incoming request is mapped to the endpoint:

```
GET /preferences/:entityId
```

where the :utr in the request has been used to find the :entityId which is the put into the ongoing request to preferences.


### Adding a nino mapping to the pathMapping configuration

Below is an example of adding a nino based path mapping configuration:

```
pathMapping = [
    {
        taxIdExtraction {
            regex = "preferences/paye/individual/([A-Z|0-9]*)/activations/notice-of-coding"
            idType = "nino"
        }
        serviceName = "preferences"
        destinationUrlPattern = "/preferences/%s/activate/notice-of-coding"
    }
]
```

This configuration would map a request of the form:

```
PUT /preferences/paye/individual/WM120612C/activations/notice-of-coding
```

to:

```
PUT /preferences/:entityId/activate/notice-of-coding
```


### Adding a mapping to the pathMapping not containing a nino or utr

Adding an entry to the pathMapping that does not need a nino or a utr would look like:

```
pathMapping = [
    {
        taxIdExtraction {
            regex = "preferences/email"
        }
        serviceName = "preferences"
        destinationUrlPattern = "/portal/preferences/email"
    }
]
```

This configuration would map an incoming request:

```
PUT /preferences/email
```

to a request to preferences of the form: 

```
PUT /portal/preference/email
```

the associated endpoint on the preferences service.


### HTTP request mapping 

The entity-resolver is HTTP request type agnostic, meaning that whichever request type it receives of PUT, POST or GET it will map to the equivalent call on the appropriate services assuming the URL path finds a matching entry in the pathMapping.
That is:

* `POST -> POST`
* `GET -> GET`
* `PUT -> PUT`


### If there is no matching mapping for a given request in the application.conf file

The entity-resolver will respond with status:

* `400` will be returned if there is no matching path in the application.conf file 

and with a response body of:

```
{
    "statusCode":400,
    "message":"No path mapping for preferences/email"
}
```

# API 

| Path                                            | Supported Methods | Description                                                                                                              |
|-------------------------------------------------|-------------------|--------------------------------------------------------------------------------------------------------------------------|
| `/entity-resolver`                              | GET               | Return entity located via the taxIds defined by the auth token in the request    [More...](#get-entity-resolverentityid) |
| `/entity-resolver?resolve=false`                | GET               | Overrides default conflict resolution mode    [More...](#get-entity-resolverentityid)                                    |
| `/entity-resolver?entityId=<value>`             | GET               | Returns the entity associated with the given entityId [More...](#get-entity-resolverentityid)                            |
| `/entity-resolver?taxRegime=sautr&taxId=<value>` | GET               | Returns the entity associated with the given utr [More...](#get-entity-resolversautr)                                    |
| `/entity-resolver/paye/:nino`                   | GET               | DEPRECATED Use below instead                                                                                             |
| `/entity-resolver?taxRegime=nino&taxId=<value>` | GET               | Returns the entity associated with the given nino [More...](#get-entity-resolverpayenino)                                |
| `/preferences/:svc/:token`                      | GET               | Returns the preferences associated with the tax identifiers returned from the service                                    |


### GET /entity-resolver
Looks for tax identifiers contained in the authorisation token of the HTTP request.
If found, uses the token the locate and entity.

Responds with status:

* `200` if the taxId is found
* `401` if the token does not exist or has expired
* `404` if there is no record matching the taxId 

Example responses:

If the entity has multiple tax identifiers, for instance SaUtr and Nino:
```
{
  "_id": "7ca752a5-14e4-4c29-bfb2-13bcd785e713",
  "sautr": "ce777fdb-55fc-4b58-a966-42fc8c1f4a2d",
  "nino": "CE039531D"
}
```

### GET /entity-resolver?resolve=false
As above, but the resolve querystring permits the overriding of the default resolution mode.
Under normal circumstances the service attempts to resolve conflicts when it finds multiple entities representing the same individual.

Setting resolve to false, the service will not resolve any conflicts and will instead return error codes.

Responds with status:

* `200` if the taxId is found
* `401` if the token does not exist or has expired
* `404` if there is no record matching the taxId 

Example responses:

If the entity has multiple tax identifiers, for instance SaUtr and Nino:
```
{
  "_id": "7ca752a5-14e4-4c29-bfb2-13bcd785e713",
  "sautr": "ce777fdb-55fc-4b58-a966-42fc8c1f4a2d",
  "nino": "CE039531D"
}
```


### GET /entity-resolver?entityId=<value>

Responds with status:

* `200` if the entityId is found
* `400` if the querystring is invalid
* `404` if there is no record matching the entityId 

Example responses:

If the entity has multiple tax identifiers, for instance SaUtr and Nino:
```
{
  "_id": "7ca752a5-14e4-4c29-bfb2-13bcd785e713",
  "sautr": "ce777fdb-55fc-4b58-a966-42fc8c1f4a2d",
  "nino": "CE039531D"
}
```
or if the entity only has a SaUtr:
```
{
  "_id": "7ca752a5-14e4-4c29-bfb2-13bcd785e713",
  "sautr": "ce777fdb-55fc-4b58-a966-42fc8c1f4a2d"
}
```
or if the entity only has a Nino:
```
{
  "_id": "7ca752a5-14e4-4c29-bfb2-13bcd785e713",
  "nino": "CE039531D"
}
```

### GET /entity-resolver?taxRegime=sautr&taxId=<value>

Responds with status:

* `200` if the utr is found
* `400` if the querystring is invalid
* `404` if there is no record matching the utr

Example responses:

If the entity has multiple tax identifiers, for instance SaUtr and Nino:
```
{
  "_id": "7ca752a5-14e4-4c29-bfb2-13bcd785e713",
  "sautr": "ce777fdb-55fc-4b58-a966-42fc8c1f4a2d",
  "nino": "CE039531D"
}
```
or if the entity only has a SaUtr:
```
{
  "_id": "7ca752a5-14e4-4c29-bfb2-13bcd785e713",
  "sautr": "ce777fdb-55fc-4b58-a966-42fc8c1f4a2d"
}
```

### GET /entity-resolver?taxRegime=nino&taxId=<value>

Responds with status:

* `200` if the nino is found
* `400` if the querystring is invalid
* `404` if there is no record matching the nino

Example responses:

If the entity has multiple tax identifiers, for instance SaUtr and Nino:
```
{
  "_id": "7ca752a5-14e4-4c29-bfb2-13bcd785e713",
  "sautr": "ce777fdb-55fc-4b58-a966-42fc8c1f4a2d",
  "nino": "CE039531D"
}
```
or if the entity only has a Nino:
```
{
  "_id": "7ca752a5-14e4-4c29-bfb2-13bcd785e713",
  "nino": "CE039531D"
}
```

### GET /preferences/:svc/:token

Responds with status:

* `400` if the service is not known
* `404` if there are no preferences recorded for mapped the tax identifiers

# Admin API
| Path                                                 | Supported Method | Description                                                                |
|------------------------------------------------------|------------------|----------------------------------------------------------------------------|
| `/entity-resolver-admin/sa/:utr`                     | GET, DELETE      | Fetch and create or delete the entity for the utr [More...](#get-entity-resolver-adminsautr)                       |
| `/entity-resolver-admin/:entityId`                   | GET, DELETE      | Determine validity for the entityId; or delete the entity [More...](#get-entity-resolver-adminentityid)            |
| `/entity-resolver-admin/paye/:nino`                  | GET              | Fetch and create the entity for the nino [More...](#get-entity-resolver-adminpayenino)                             |
| `/entity-resolver-admin/manual-opt-out/:regime/:utr` | POST             | Opt the user out of paperless [More...](#post-entity-resolver-adminmanual-opt-out)                                 |
  
### POST /entity-resolver-admin/manual-opt-out/:regime/:utr

This is documented here, but must not be used without understanding of the consequences to the customer. 
This call will cause the given customer's preference to be altered to be opted out of paperless communications.
This is audited in both entity-resolver as a manual opt-out and also audited as an opt-out in the preferences service.

The only 2 tax idenifiers are PAYE for NINO and SA for UTR

Responds with status:

* `200` if the entity is found for the utr and the user is opted out
* `404` if the preference for utr is not found
* `409` if the utr is found and is already opted out
* `412` if the entity for utr is not found


### GET /entity-resolver-admin/sa/:utr

Responds with status:

* `200` if the entity is found for the utr
Example response with body:

```
7ca752a5-14e4-4c29-bfb2-13bcd785e713
```
* `201` if no entity found for the utr, will create one 
Example response with body:

```
7ca752a5-14e4-4c29-bfb2-13bcd785e713
```

### DELETE /entity-resolver-admin/sa/:utr

Responds with status:
 
* `200` if the entity and the preference are removed
* `204` if no entity found for the utr 

### GET /entity-resolver-admin/:entityId

Responds with status:

* `200` if the entityId is valid
* `404` if the entityId is invalid 

### DELETE /entity-resolver-admin/:entityId

Responds with status:
 
* `200` if the entity is removed
* `404` if the entity is not found or not removed 

### GET /entity-resolver-admin/paye/:nino

Responds with status:

* `200` if the entity is found for the nino
Example response with body:

```
7ca752a5-14e4-4c29-bfb2-13bcd785e713
```
* `201` if no entity found for the nino, will create one 
Example response with body:

```
7ca752a5-14e4-4c29-bfb2-13bcd785e713
```
