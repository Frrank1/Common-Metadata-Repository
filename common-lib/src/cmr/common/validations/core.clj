(ns cmr.common.validations.core
  "Validation framework created from scratch. Both bouncer and validateur are Clojure Validation
  frameworks but they have bugs and limitations that made them inappropriate for use.

  TODO complete documentation not written yet. I will first make sure that we have something that
  will work for our needs before writing the documentation.

  Quick documentation notes:
  A validation is a function.
  It takes 2 arguments a field path vector and a value. It returns either nil or a map of field
  paths to a list of errors.

  "
  (:require [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Support functions

(declare auto-validation-convert)

(defn record-validation
  "Converts a map into a record validator"
  [field-map]
  (fn [field-path value]
    (when value
      (reduce (fn [field-errors [validation-field validation]]
                (let [validation (auto-validation-convert validation)
                      errors (validation (conj field-path validation-field)
                                         (validation-field value))]
                  (if (seq errors)
                    (merge field-errors errors)
                    field-errors)))
              {}
              field-map))))

(defn seq-of-validations
  "Returns a validator merging results from a list of validators."
  [validators]
  (let [validators (map auto-validation-convert validators)]
    (fn [field-path value]
      (reduce (fn [field-errors validator]
                (let [errors (validator field-path value)]
                  (if (seq errors)
                    (merge field-errors errors)
                    field-errors)))
              {}
              validators))))

(defn auto-validation-convert
  "Handles converting basic clojure data structures into a validation function."
  [validation]
  ;; TODO consider using protocols or other dispatch method
  (cond
    (map? validation) (record-validation validation)
    (sequential? validation) (seq-of-validations validation)
    :else validation))

(defn- humanize-field
  "Converts a keyword to a humanized field name"
  [field]
  (when field (str/replace (str/capitalize (name field)) #"-" " ")))

(defn create-error-messages
  "TODO"
  [field-errors]
  (for [[field-path errors] field-errors
        :when (seq errors)
        :let [field (last field-path)]
        error errors]
    (format error (humanize-field field))))

(defn validate
  "Validates the given value with the validation. Returns a map of fields to error formats."
  [validation value]
  (let [validation (auto-validation-convert validation)]
    (validation [] value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validations

(defn pre-validation
  "Runs a function on the value before validation begins. The result of prefn is passed to the
  validation."
  [prefn validation]
  (let [validation (auto-validation-convert validation)]
    (fn [field-path value]
      (validation field-path (prefn value)))))

(defn required
  "Validates that the value is not nil"
  [field-path value]
  (when (nil? value)
    {field-path ["%s is required."]}))

(defn every
  "Validate a validation against every item in a sequence. The field path will contain the index of
  the item that had an error"
  [validation]
  (let [validator (auto-validation-convert validation)]
    (fn [field-path values]
      (let [error-maps (for [[idx value] (map-indexed vector values)
                             :let [errors-map (validator (conj field-path idx) value)]
                             :when (seq errors-map)]
                         errors-map)]
        (apply merge-with concat error-maps)))))

(defn integer
  "Validates that the value is an integer"
  [field-path value]
  (when (and value (not (integer? value)))
    {field-path [(format "%%s must be an integer but was [%s]." value)]}))

(defn number
  "Validates the value is a number"
  [field-path value]
  (when (and value (not (number? value)))
    {field-path [(format "%%s must be a number but was [%s]" value)]}))

(defn within-range
  "Creates a validator within a specified range"
  [minv maxv]
  (fn [field-path value]
    (when (and value (or (< (compare value minv) 0) (> (compare value maxv) 0)))
      {field-path [(format "%%s must be within [%s] and [%s] but was [%s]"
                           minv maxv value)]})))


(comment

  ((seq-of-validations
     [required integer])
   [:foo] 5)

  ((record-validation {:a [required number]
                       :b [{:name required} (fn [f _] {f ["always fail"]})]})
   [:foo] {:b {:name "foo"}})

  ((record-validation {:a [required number]
                       :b [required (fn [f _] {f ["always fail"]})]})
   [:foo] {:b {:name "foo"}})



  (def address-validations
    {:city required
     :street required})

  (defn last-not-first
    [field-path person-name]
    (when (= (:last person-name) (:first person-name))
      {field-path ["Last name must not equal first name"]}))

  (def person-validations
    {:addresses (every address-validations)
     :name [{:first required
             :last required}
            last-not-first]
     :age [required integer]})

  (validate person-validations {:addresses [{:street "5 Main"
                                             :city "Annapolis"}
                                            {:city "dd"}
                                            {:city "dd"}
                                            {:street "dfkkd"}]
                                :name {:first "Jason"
                                       :last "Jason"}
                                :age "35"})

  )