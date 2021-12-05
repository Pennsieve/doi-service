#####SCRIPT IS NOT YET FUNCTIONAL, DO NOT RUN!

#Script for updating the remaining SPARC datasets DOI URLs which are still pointing to a pennsieve location

#DataCiteAPI reference: https://support.datacite.org/docs
#example of DataCite DOI JSON object: https://api.datacite.org/dois/application/vnd.datacite.datacite+json/10.26275/9ffg-482d
#BF DataCIte Repository: https://doi.datacite.org/repositories/bf.discover

pip install datacite
pip install requests
import requests

#Process is correct, but for some reason the returned doi are wrong (don't correspond to Pennsive or SPARC dois)

#there are 624 DOI records to migrate, so have them all appear on one page
url = "https://api.datacite.org/repositories/bf.discover/dois?page[size]=630"

headers = {"Accept": "application/vnd.api+json"}

response = requests.request("GET",url,headers=headers)

#prints the json object as a list
print(response.text)

#Retrieving DOIs with SPARC as the publisher
import json
import re

response_str = response.text

# Transform json input to python dictionary
input_dict = json.loads(response_str)

print(type(input_dict))

# Filter by publisher with list comprehensions
#ERROR: String indicies must be integers? Above indicates that it is a dictionary (perhaps more nested than json examples indicate)
output_dict = [x for x in input_dict if x["publisher"] == "SPARC Consortium"]

#updating the URLS of the DOI JSON objects
url =""
doi = ""
for i in output_dict:
  #original url
  url = i["url"]
  #doi identifier
  doi = i["doi"]
  #URL for PUT request
  url_fetch = "https://api.datacite.org/dois/{}".format(doi)
  id = ""
  version = ""
  
  #retrieving id and version from original url
  x = re.search('datasets/(.+?)/version', url)
  if x:
    id = x.group(1)
  y = re.search('/version(.+?)', url)
  if y:
    version = y.group(1)

  #
  payload = "{\"data\":{\"attributes\":{\"identifiers\":[{}],\"url\":\"https://sparc.science/datasets/{}/version/{}\"}}}".format(id,version)
  headers = {
    "Content-Type": "application/vnd.api+json",
    #"Authorization": "Basic MTIzNDoyMjEy"
  }
  #sends updated url (Authorization may be needed in header
  response = requests.request("PUT", url_fetch, data=payload, headers=headers,auth=('BR.DISCOVER','K5Q_*0koOHdV'))
  print(response.text)
