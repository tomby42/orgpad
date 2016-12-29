import sys, json, re, urllib

if len(sys.argv) < 2:
    print "usage: orgpad1to2.py orgpad1_html_file"
    sys.exit(1)

htmlFile = file(sys.argv[1], "r")
html = htmlFile.read()
m = re.search(r"<input type=\"hidden\" name=\"content\" value=\"(.*)\"\s*\/*>", html)

orgpad1json = json.loads(urllib.unquote(m.group(1)))

# print json.dumps(orgpad1json, sort_keys = True, indent = 2)

prefix = "#orgpad/DatomAtomStore {:datom #orgpad/DatomStore {:db #datascript/DB {:schema {:orgpad/unit-height {}, :orgpad/view-name {}, :orgpad/unit-border-color {}, :orgpad/view-path {}, :orgpad/unit-position {}, :orgpad/refs-order {}, :orgpad/unit-width {}, :orgpad/unit-visibility {}, :orgpad/atom {}, :orgpad/link-dash {}, :orgpad/props-refs {:db/valueType :db.type/ref, :db/cardinality :db.cardinality/many}, :orgpad/link-mid-pt {}, :orgpad/desc {}, :orgpad/tags {:db/cardinality :db.cardinality/many}, :orgpad/view-type {}, :orgpad/unit-border-style {}, :orgpad/refs {:db/valueType :db.type/ref, :db/cardinality :db.cardinality/many}, :orgpad/unit-corner-y {}, :orgpad/transform {}, :orgpad/link-width {}, :orgpad/link-color {}, :orgpad/active-unit {}, :orgpad/view-stack {:db/cardinality :db.cardinality/many}, :orgpad/unit-border-width {}, :orgpad/type {}, :orgpad/unit-corner-x {}, :orgpad/unit-bg-color {}}, :datoms ["

initialUnits = """[0 :orgpad/props-refs 1 536870913]
[0 :orgpad/type :orgpad/root-unit 536870913]
[1 :orgpad/refs 0 536870913]
[1 :orgpad/type :orgpad/root-unit-view 536870913]"""

suffix = "]}} :atom {:mode :write}}"

txStamp = 536870913

nodes = {}
links = {}
nodeAttrs = {}
linkAttrs = {}

for wid in orgpad1json["dataModel"]["datas"]:
    nodes[int(wid)] = orgpad1json["dataModel"]["datas"][wid]

for aid in orgpad1json["dataModel"]["links"]:
    links[int(aid)] = orgpad1json["dataModel"]["links"][aid]

for w in orgpad1json["herbartModel"]["whiles"]:
    nodeAttrs[w["wid"]] = w

for a in orgpad1json["herbartModel"]["associations"]:
    linkAttrs[a["aid"]] = a

maxWID = max(nodes.keys())
maxAID = max(links.keys())
newUID = maxWID + maxAID + 3

def nextUID():
    globals()['newUID'] += 1
    return globals()['newUID']

def getBorderStyle(dash):
    if dash == '1,0':
        return 'solid'
    dashes = dash.split(',')
    if len(dashes) == 2 and dashes[0] == dashes[1]:
        return 'dotted'
    return 'dashed'

def printLevelProps(uid, data):
    print '[%d :orgpad/unit-width %s %d]' % (uid, data["levelSize"][0], txStamp)
    print '[%d :orgpad/unit-height %s %d]' % (uid, data["levelSize"][1], txStamp)
    print '[%d :orgpad/unit-border-color "%s" %d]' % (uid, data["levelBorderColor"], txStamp)
    print '[%d :orgpad/unit-bg-color "%s" %d]' % (uid, data["levelBgColor"], txStamp)
    print '[%d :orgpad/unit-border-width %s %d]' % (uid, data["levelBorderWidth"], txStamp)
    print '[%d :orgpad/unit-corner-x %s %d]' % (uid, data["levelBorderCorner"][0], txStamp)
    print '[%d :orgpad/unit-corner-y %s %d]' % (uid, data["levelBorderCorner"][1], txStamp)
    print '[%d :orgpad/unit-border-style "%s" %d]' % (uid, getBorderStyle(data["levelBorderDash"]), txStamp)

def printSheet(parentUID, modelData, attrData):
    uid = nextUID()
    propUID = nextUID()
    print '[%d :orgpad/refs %d %d]' % (parentUID, uid, txStamp)
    print '[%d :orgpad/type :orgpad/unit %d]' % (uid, txStamp)
    print '[%d :orgpad/props-refs %d %d]' % (uid, propUID, txStamp)
    # vertex props propagated
    print '[%d :orgpad/refs %d %d]' % (propUID, uid, txStamp)
    print '[%d :orgpad/type :orgpad/unit-view-child-propagated %d]' % (propUID, txStamp)
    print '[%d :orgpad/view-name "default" %d]' % (propUID, txStamp)
    print '[%d :orgpad/view-type :orgpad.map-view/vertex-props %d]' % (propUID, txStamp)
    print '[%d :orgpad/unit-visibility true %d]' % (propUID, txStamp)
    printLevelProps(propUID, attrData)
    # atomic view
    atomicUID = nextUID()
    print '[%d :orgpad/props-refs %d %d]' % (uid, atomicUID, txStamp)
    print '[%d :orgpad/refs %d %d]' % (atomicUID, uid, txStamp)
    print '[%d :orgpad/type :orgpad/unit-view %d]' % (atomicUID, txStamp)
    print '[%d :orgpad/view-name "default" %d]' % (atomicUID, txStamp)
    print '[%d :orgpad/view-type :orgpad/atomic-view %d]' % (atomicUID, txStamp)
    print '[%d :orgpad/atom "%s" %d]' % (atomicUID, modelData, txStamp)

def printUnit(_uid, modelData, attrData):
    uid = _uid + 2
    propUID = nextUID()
    # unit
    print '[0 :orgpad/refs %d %d]' % (uid, txStamp)
    print '[%d :orgpad/type :orgpad/unit %d]' % (uid, txStamp)
    print '[%d :orgpad/props-refs %d %d]' % (uid, propUID, txStamp)
    # vertex props unit
    print '[%d :orgpad/refs %d %d]' % (propUID, uid, txStamp)
    print '[%d :orgpad/type :orgpad/unit-view-child %d]' % (propUID, txStamp)
    print '[%d :orgpad/view-name "default" %d]' % (propUID, txStamp)
    print '[%d :orgpad/unit-position [%d %d] %d]' % (propUID, attrData['pos']['x'], attrData['pos']['y'], txStamp)
    print '[%d :orgpad/context-unit %d %d]' % (propUID, 0, txStamp)
    print '[%d :orgpad/unit-visibility true %d]' % (propUID, txStamp)
    print '[%d :orgpad/view-type :orgpad.map-view/vertex-props %d]' % (propUID, txStamp)
    printLevelProps(propUID, attrData['state']['attrs']['0'])
    #print "modelData", modelData['data']
    #print "attrData", attrData['state']['attrs']
    for i in xrange(0,3):
        mdata = ''
        if str(i) in modelData['data']:
            mdata = modelData['data'][str(i)]
        printSheet(uid, mdata, attrData['state']['attrs'][str(i)])

def getMidPt(isCurved):
    if isCurved:
        return [20, 20]
    return [0, 0]

def printLink(_uid, modelData, attrData):
    uid = maxWID + _uid + 3
    propUID = nextUID()
    # link
    print '[0 :orgpad/refs %d %d]' % (uid, txStamp)
    print '[%d :orgpad/type :orgpad/unit %d]' % (uid, txStamp)
    print '[%d :orgpad/props-refs %d %d]' % (uid, propUID, txStamp)
    print '[%d :orgpad/refs %d %d]' % (uid, modelData['nid1'] + 2, txStamp)
    print '[%d :orgpad/refs %d %d]' % (uid, modelData['nid2'] + 2, txStamp)
    print '[%d :orgpad/refs-order #{["!!!!" %d] ["!!!1" %d]} %d]' % (uid, modelData['nid1'] + 2, modelData['nid2'] + 2, txStamp)
    # link props
    midPt = getMidPt(attrData['curved'])
    print '[%d :orgpad/refs %d %d]' % (propUID, uid, txStamp)
    print '[%d :orgpad/type :orgpad/unit-view-child %d]' % (propUID, txStamp)
    print '[%d :orgpad/view-type :orgpad.map-view/link-props %d]' % (propUID, txStamp)
    print '[%d :orgpad/view-name "default" %d]' % (propUID, txStamp)
    print '[%d :orgpad/context-unit %d %d]' % (propUID, 0, txStamp)
    print '[%d :orgpad/link-mid-pt [%d %d] %d]' % (propUID, midPt[0], midPt[1], txStamp)
    print '[%d :orgpad/link-color "%s" %d]' % (propUID, attrData['color'], txStamp)
    print '[%d :orgpad/link-width %s %d]' % (propUID, attrData['weight'], txStamp)
    print '[%d :orgpad/link-dash #js [0 0] %d]' % (propUID, txStamp)

def printCanvasTransform(tr):
    propUID = nextUID()
    m = re.search(r"translate\s*\(([-0-9.]*) ([-0-9.]*)\)", tr)
    print '[0 :orgpad/props-refs %d %d]' % (propUID, txStamp)
    print '[%d :orgpad/refs 0 %d]' % (propUID, txStamp)
    print '[%d :orgpad/type :orgpad/unit-view %d]' % (propUID, txStamp)
    print '[%d :orgpad/view-name "default" %d]' % (propUID, txStamp)
    print '[%d :orgpad/view-type :orgpad/map-view %d]' % (propUID, txStamp)
    print '[%d :orgpad/transform {:translate [%s %s], :scale 1} %d]' % (propUID, m.group(1), m.group(2), txStamp)


print prefix
print initialUnits
printCanvasTransform(orgpad1json['canvas']['transf'])

for nid in nodes:
    printUnit(nid, nodes[nid], nodeAttrs[nid])

for aid in links:
    try:
        printLink(aid, links[aid], linkAttrs[aid])
    except:
        pass


print suffix

"""
{
  "canvas": {
    "transf": "translate (0 0) rotate (0) scale (1 1) translate (0 0)"
  },
  "dataModel": {
    "datas": {
      "0": {
        "attrs": {},
        "data": {
          "0": "a1",
          "1": "a2",
          "2": "a3"
        },
        "fixed": false
      },
      "1": {
        "attrs": {},
        "data": {
          "0": "b1",
          "1": "b2",
          "2": "b3"
        },
        "fixed": false
      }
    },
    "links": {
      "0": {
        "data": {
          "attrs": {},
          "data": {},
          "fixed": false
        },
        "nid1": 0,
        "nid2": 1
      }
    }
  },
  "herbartModel": {
    "associations": [
      {
        "aid": 0,
        "arrowed": true,
        "awid": -1,
        "color": "#009cff",
        "curved": true,
        "ewid": 1,
        "initState": 0,
        "nofLevel": 1,
        "swid": 0,
        "weight": "1"
      }
    ],
    "whiles": [
      {
        "initState": 0,
        "nofLevels": 3,
        "pos": {
          "x": 927,
          "y": 419
        },
        "state": {
          "attrs": {
            "0": {
              "levelBgColor": "#ffffff",
              "levelBorderColor": "#009cff",
              "levelBorderCorner": [
                "5",
                "5"
              ],
              "levelBorderDash": "1,0",
              "levelBorderWidth": "2",
              "levelContentScroll": [
                0,
                0
              ],
              "levelName": "",
              "levelSize": [
                "250",
                "60"
              ]
            },
            "1": {
              "levelBgColor": "#ffffff",
              "levelBorderColor": "#009cff",
              "levelBorderCorner": [
                "5",
                "5"
              ],
              "levelBorderDash": "1,0",
              "levelBorderWidth": "2",
              "levelContentScroll": [
                0,
                0
              ],
              "levelName": "",
              "levelSize": [
                "320",
                "150"
              ]
            },
            "2": {
              "levelBgColor": "#ffffff",
              "levelBorderColor": "#009cff",
              "levelBorderCorner": [
                "5",
                "5"
              ],
              "levelBorderDash": "1,0",
              "levelBorderWidth": "2",
              "levelContentScroll": [
                0,
                0
              ],
              "levelName": "",
              "levelSize": [
                "400",
                "400"
              ]
            }
          },
          "data": {},
          "fixed": false
        },
        "wid": 0
      },
      {
        "initState": 0,
        "nofLevels": 3,
        "pos": {
          "x": 1454,
          "y": 599
        },
        "state": {
          "attrs": {
            "0": {
              "levelBgColor": "#ffffff",
              "levelBorderColor": "#009cff",
              "levelBorderCorner": [
                "5",
                "5"
              ],
              "levelBorderDash": "1,0",
              "levelBorderWidth": "2",
              "levelContentScroll": [
                0,
                0
              ],
              "levelName": "",
              "levelSize": [
                "250",
                "60"
              ]
            },
            "1": {
              "levelBgColor": "#ffffff",
              "levelBorderColor": "#009cff",
              "levelBorderCorner": [
                "5",
                "5"
              ],
              "levelBorderDash": "1,0",
              "levelBorderWidth": "2",
              "levelContentScroll": [
                0,
                0
              ],
              "levelName": "",
              "levelSize": [
                "320",
                "150"
              ]
            },
            "2": {
              "levelBgColor": "#ffffff",
              "levelBorderColor": "#009cff",
              "levelBorderCorner": [
                "5",
                "5"
              ],
              "levelBorderDash": "1,0",
              "levelBorderWidth": "2",
              "levelContentScroll": [
                0,
                0
              ],
              "levelName": "",
              "levelSize": [
                "400",
                "400"
              ]
            }
          },
          "data": {},
          "fixed": false
        },
        "wid": 1
      }
    ]
  },
  "journal": {
    "history": [],
    "speed": 1,
    "useGlobal": true
  }
}
"""


"""
#orgpad/DatomAtomStore {:datom #orgpad/DatomStore {:db #datascript/DB {:schema {:orgpad/unit-height {}, :orgpad/view-name {}, :orgpad/unit-border-color {}, :orgpad/view-path {}, :orgpad/unit-position {}, :orgpad/refs-order {}, :orgpad/unit-width {}, :orgpad/unit-visibility {}, :orgpad/atom {}, :orgpad/link-dash {}, :orgpad/props-refs {:db/valueType :db.type/ref, :db/cardinality :db.cardinality/many}, :orgpad/link-mid-pt {}, :orgpad/desc {}, :orgpad/tags {:db/cardinality :db.cardinality/many}, :orgpad/view-type {}, :orgpad/unit-border-style {}, :orgpad/refs {:db/valueType :db.type/ref, :db/cardinality :db.cardinality/many}, :orgpad/unit-corner-y {}, :orgpad/transform {}, :orgpad/link-width {}, :orgpad/link-color {}, :orgpad/active-unit {}, :orgpad/view-stack {:db/cardinality :db.cardinality/many}, :orgpad/unit-border-width {}, :orgpad/type {}, :orgpad/unit-corner-x {}, :orgpad/unit-bg-color {}}, :datoms [
[0 :orgpad/props-refs 1 536870913]
[0 :orgpad/refs 2 536870914]
[0 :orgpad/refs 6 536870915]
[0 :orgpad/refs 11 536870920]
[0 :orgpad/type :orgpad/root-unit 536870913]
[1 :orgpad/refs 0 536870913]
[1 :orgpad/type :orgpad/root-unit-view 536870913]
[2 :orgpad/props-refs 3 536870914]
[2 :orgpad/refs 4 536870914]
[2 :orgpad/type :orgpad/unit 536870914]
[3 :orgpad/context-unit 0 536870914]
[3 :orgpad/refs 2 536870914]
[3 :orgpad/type :orgpad/unit-view-child 536870914]
[3 :orgpad/unit-bg-color "#ffffff" 536870914]
[3 :orgpad/unit-border-color "#009cff" 536870914]
[3 :orgpad/unit-border-style "solid" 536870914]
[3 :orgpad/unit-border-width 2 536870914]
[3 :orgpad/unit-corner-x 5 536870914]
[3 :orgpad/unit-corner-y 5 536870914]
[3 :orgpad/unit-height 60 536870914]
[3 :orgpad/unit-position [585 234] 536870914]
[3 :orgpad/unit-visibility true 536870914]
[3 :orgpad/unit-width 250 536870914]
[3 :orgpad/view-name "default" 536870914]
[3 :orgpad/view-type :orgpad.map-view/vertex-props 536870914]
[4 :orgpad/props-refs 5 536870914]
[4 :orgpad/props-refs 10 536870917]
[4 :orgpad/type :orgpad/unit 536870914]
[5 :orgpad/refs 4 536870914]
[5 :orgpad/type :orgpad/unit-view-child-propagated 536870914]
[5 :orgpad/unit-bg-color "#ffffff" 536870914]
[5 :orgpad/unit-border-color "#009cff" 536870914]
[5 :orgpad/unit-border-style "solid" 536870914]
[5 :orgpad/unit-border-width 2 536870914]
[5 :orgpad/unit-corner-x 5 536870914]
[5 :orgpad/unit-corner-y 5 536870914]
[5 :orgpad/unit-height 60 536870914]
[5 :orgpad/unit-width 250 536870914]
[5 :orgpad/view-name "default" 536870914]
[5 :orgpad/view-type :orgpad.map-view/vertex-props 536870914]
[6 :orgpad/props-refs 7 536870915]
[6 :orgpad/refs 8 536870915]
[6 :orgpad/type :orgpad/unit 536870915]
[7 :orgpad/context-unit 0 536870915]
[7 :orgpad/refs 6 536870915]
[7 :orgpad/type :orgpad/unit-view-child 536870915]
[7 :orgpad/unit-bg-color "#ffffff" 536870915]
[7 :orgpad/unit-border-color "#009cff" 536870915]
[7 :orgpad/unit-border-style "solid" 536870915]
[7 :orgpad/unit-border-width 2 536870915]
[7 :orgpad/unit-corner-x 5 536870915]
[7 :orgpad/unit-corner-y 5 536870915]
[7 :orgpad/unit-height 60 536870915]
[7 :orgpad/unit-position [940 416] 536870915]
[7 :orgpad/unit-visibility true 536870915]
[7 :orgpad/unit-width 250 536870915]
[7 :orgpad/view-name "default" 536870915]
[7 :orgpad/view-type :orgpad.map-view/vertex-props 536870915]
[8 :orgpad/props-refs 9 536870915]
[8 :orgpad/props-refs 13 536870922]
[8 :orgpad/type :orgpad/unit 536870915]
[9 :orgpad/refs 8 536870915]
[9 :orgpad/type :orgpad/unit-view-child-propagated 536870915]
[9 :orgpad/unit-bg-color "#ffffff" 536870915]
[9 :orgpad/unit-border-color "#009cff" 536870915]
[9 :orgpad/unit-border-style "solid" 536870915]
[9 :orgpad/unit-border-width 2 536870915]
[9 :orgpad/unit-corner-x 5 536870915]
[9 :orgpad/unit-corner-y 5 536870915]
[9 :orgpad/unit-height 60 536870915]
[9 :orgpad/unit-width 250 536870915]
[9 :orgpad/view-name "default" 536870915]
[9 :orgpad/view-type :orgpad.map-view/vertex-props 536870915]
[10 :orgpad/atom "<p>a1</p>" 536870918]
[10 :orgpad/refs 4 536870917]
[10 :orgpad/type :orgpad/unit-view 536870917]
[10 :orgpad/view-name "default" 536870917]
[10 :orgpad/view-type :orgpad/atomic-view 536870917]
[11 :orgpad/props-refs 12 536870920]
[11 :orgpad/refs 2 536870920]
[11 :orgpad/refs 6 536870920]
[11 :orgpad/refs-order #{["!!!!" 2] ["!!!! " 6]} 536870920]
[11 :orgpad/type :orgpad/unit 536870920]
[12 :orgpad/context-unit 0 536870920]
[12 :orgpad/link-color "#000000" 536870920]
[12 :orgpad/link-dash #js [0 0] 536870920]
[12 :orgpad/link-mid-pt [0 0] 536870920]
[12 :orgpad/link-width 2 536870920]
[12 :orgpad/refs 11 536870920]
[12 :orgpad/type :orgpad/unit-view-child 536870920]
[12 :orgpad/view-name "default" 536870920]
[12 :orgpad/view-type :orgpad.map-view/link-props 536870920]
[13 :orgpad/atom "<p>b1</p>" 536870922]
[13 :orgpad/refs 8 536870922]
[13 :orgpad/type :orgpad/unit-view 536870922]
[13 :orgpad/view-name "default" 536870922]
[13 :orgpad/view-type :orgpad/atomic-view 536870922]
]}} :atom {:mode :write}}
"""
