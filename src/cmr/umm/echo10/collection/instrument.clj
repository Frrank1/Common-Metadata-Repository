(ns cmr.umm.echo10.collection.instrument
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]))

(defn xml-elem->Instrument
  [instrument-elem]
  (let [short-name (cx/string-at-path instrument-elem [:ShortName])]
    (c/->Instrument short-name)))

(defn xml-elem->Instruments
  [platform-element]
  (seq (map xml-elem->Instrument
            (cx/elements-at-path
              platform-element
              [:Instruments :Instrument]))))

(defn generate-instruments
  [instruments]
  (when-not (empty? instruments)
    (x/element
      :Instruments {}
      (for [instrument instruments]
        (let [{:keys [short-name]} instrument]
          (x/element :Instrument {}
                     (x/element :ShortName {} short-name)))))))
