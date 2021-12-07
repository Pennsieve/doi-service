#!/usr/bin/env python3

#Script for updating the remaining SPARC datasets DOI URLs which are still pointing to a pennsieve location

#DataCiteAPI reference: https://support.datacite.org/docs
#example of DataCite DOI JSON object: https://api.datacite.org/dois/application/vnd.datacite.datacite+json/10.26275/9ffg-482d
#BF DataCIte Repository: https://doi.datacite.org/repositories/bf.discover

# Install the below packages before executing:
#   pip install datacite
#   pip install requests

import requests
import json
import re

url = "https://api.pennsieve.io/discover/search/datasets?limit=150&offset=0&organizationId=367&orderBy=relevance&orderDirection=desc"
headers = {"Accept": "application/json"}

response = requests.request("GET", url, headers=headers)

print(response.text)

# Transform json input to python dictionary
input_dict = json.loads(response.text)['datasets']

print(type(input_dict))

#updating the URLS of the DOI JSON objects
for i in input_dict:
  #doi identifier
  doi = i["doi"]
  #URL for PUT request
  url_fetch = "https://api.datacite.org/dois/{}".format(doi)
  id = i['id']
  version = i['version']
  
  payload = {}
  payload['data'] = {}
  payload['data']['attributes'] = { 'publisher': 'SPARC Consortium', 'url': "https://sparc.science/datasets/{}/version/{}".format(id, version) }
  
  headers = {
    "Content-Type": "application/vnd.api+json",
    #"Authorization": "Basic MTIzNDoyMjEy"
  }
  #sends updated url (Authorization may be needed in header
  response = requests.request("PUT", url_fetch, data=json.dumps(payload), headers=headers,auth=('BF.DISCOVER','K5Q_*0koOHdV'))
  print(response.text)
