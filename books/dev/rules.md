# Rules (Reglas a nivel de documentos en la base de datos)

Objetivo general establecer restricciones a nivel de documentos.

- [] Añadir soporte para integridad referencial cuando son documentos referenciados.  es decir tengo un documento por ejemplo 
Tengo dos colecciones: pais y persona. En la coleccion de persona usa una referencia a la coleccion Pais
pais{
[
{
  "_id": "6930f21cdf1d47955269c72c","codigo":"pa", "nombre":"Panama"
} 
,
{
  "_id": "6930effc74de2e6c2f6805af","codigo":"co", "nombre":"Colombia"
}
]
}

La coleccion persona tiene una estructura similar a a, puede observar que cuanto tiene un documento embebido que en su interior
usa _id indica que es una referencia a otra coleccion y ese es el _id de la coleccion primaria. En este caso la persona ana
esta asignada al pais panama.

persona [
       {
         "_id":"6930f14ddf1d47955269c72b", "cedula":"7","nombre":"ana", "pais":{"_id": "6930f21cdf1d47955269c72c"}
       }
].

Este analisis nos lleva a dos tipos de documentos, los que son libres de esquemas en los que no se necesita definir un esquema
y pueden almacenar cualquier tipo de información de manera libre y otro tipo que son documentos con esquemas estos documentos
definen las referencias entre documentos.
En este caso la cada base de datos debe llevar un documento especial llamado _rules donde se definan reglas por ejemplo.


_rules{
[
{
  "persona":[
             {
              "pais":
                    {                                                      
                     "type":"referenced"
                     "collectionreferenced":"pais",
                     "externalfield":"_id"
                     }
             },                          
             {
              "nombre":
                    {
                    "type":"validation"
                    "value":"notnull"
                    }                           
              }
            ]
,
 "pais":[
         {
          "codigo":
                   {
                   "type":"validation":
                   "value":"notnull"
                   }
         }
         ]
}
]

Como puede observar se define dentro de rules un arreglo de documento el primer valor representa la coleccion a aplicar la regla
dentro de el se crea un arreglo de reglas, el primer valor corresponde al nombre del campo y dentro de el un documento embebido
va a representar las reglas de validacion por ejemplo: type (referenced) indica que hay una coleccion referenciada y no se puede
insertar un valor en el campo pais si no existe ese _id en la coleccion pais. "collectionreferenced" es la coleccion referenciada
sobre la que se debe hacer la consulta y externalfield es el campo en la coleccion externa sobre la que se desea realizar la consulta
generalmente se usa el _id para garantizar la integridad.
Otras validaciones son mas sencillas por ejemplo. type: validation indica que es un validacion simple y en value colocamos la condicion
tal como notnull . max , min o betweeen para valores en ese rango.
Crees que puedes modificar el engine para adaptarlo a estos requerimientos.




## Ejemplo de reglas de validacion

```json

{
  "_id": "6931ab1b85aeb25b41db4e77",
  "persona": [
    {
      "pais": {
        "collectionreferenced": "pais",
        "externalfield": "_id",
        "type": "referenced"
      }
    },
    {
      "nombre": {
        "type": "validation",
        "value": "notnull"
      }
    }
  ]
}

```

Implementation Plan - New Validation Rules
The user wants to add several new validation rules to the database engine.

User Review Required
NOTE

The new rules will be implemented as validation types in the _rules collection.

not_null: Ensures value is not null (already exists as notnull, will add alias).
not_empty: Ensures strings/lists are not empty.
non_negative: Ensures numeric values are >= 0.
min_value: Ensures numeric values are >= specified value.
max_value: Ensures numeric values are <= specified value.
range: Ensures numeric values are within [min, max].
Proposed Changes
Backend Logic
[MODIFY] 
storage/validation.go
Update 
applyRule
 function to handle new rule.Value cases:
not_empty: Check if string is "" or list/map is empty.
non_negative: Check if number is < 0.
min_value: Parse rule.Value as float/int and compare.
max_value: Parse rule.Value as float/int and compare.
range: Parse rule.Value as "min,max" and compare.
Verification Plan
Automated Tests
Create a shell script sh/testing/verify_new_rules.sh to:
Define rules for a test collection.
Insert valid documents.
Attempt to insert invalid documents for each rule and verify failure.

